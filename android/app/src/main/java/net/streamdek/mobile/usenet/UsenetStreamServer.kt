package net.streamdek.mobile.usenet

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

private const val TAG = "StreamDekUsenet"
private const val CHUNK_BYTES = 256 * 1024

/**
 * Serves assembled usenet downloads to the player over loopback.
 *
 * The player needs a URL it can range-request and seek within; a usenet post is a pile of articles
 * that arrive out of order. This bridges the two: it binds to 127.0.0.1 only, so nothing outside
 * the device can reach it, and each request blocks on [UsenetStreamSession.awaitAvailable] until
 * the bytes it asked for have been pulled and decoded.
 */
class UsenetStreamServer {
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val acceptExecutor = Executors.newSingleThreadExecutor()
    private val requestExecutor = Executors.newCachedThreadPool()
    private val sessions = ConcurrentHashMap<String, UsenetStreamSession>()

    @Volatile
    var port: Int = 0
        private set

    fun ensureStarted(): Int {
        if (running.get() && port > 0) return port
        synchronized(this) {
            if (running.get() && port > 0) return port
            // Port 0 lets the OS pick a free one — a fixed port collides with whatever else the
            // device is running, and there is nothing outside this process that needs to guess it.
            val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
            socket.reuseAddress = true
            serverSocket = socket
            port = socket.localPort
            running.set(true)
            acceptExecutor.execute {
                while (running.get()) {
                    try {
                        val client = socket.accept()
                        requestExecutor.execute { handle(client) }
                    } catch (_: SocketException) {
                        running.set(false)
                    } catch (_: Throwable) {
                        // Keep accepting; one bad connection must not take the server down.
                    }
                }
            }
            Log.i(TAG, "Usenet stream server listening on 127.0.0.1:$port")
            return port
        }
    }

    fun register(session: UsenetStreamSession): String {
        sessions.put(session.id, session)?.takeIf { it !== session }?.close()
        return "http://127.0.0.1:${ensureStarted()}/usenet/${session.id}"
    }

    fun release(sessionId: String) {
        sessions.remove(sessionId)?.close()
    }

    fun releaseAll() {
        sessions.keys.toList().forEach(::release)
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 120_000
            val input = client.getInputStream().buffered()
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0].uppercase(Locale.US)
            val path = parts[1]
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isBlank()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    headers[line.take(separator).trim().lowercase(Locale.US)] = line.substring(separator + 1).trim()
                }
            }
            val sessionId = path.substringAfterLast('/').substringBefore('?')
            val session = sessions[sessionId]
            val output = client.getOutputStream()
            if (session == null) {
                writeStatus(output, 404, "Not Found")
                return
            }
            runCatching { serve(session, method, headers["range"], output) }
                .onFailure { if (it !is SocketException) Log.w(TAG, "Usenet request failed", it) }
        }
    }

    private fun serve(session: UsenetStreamSession, method: String, rangeHeader: String?, output: OutputStream) {
        val total = session.totalBytes
        if (total <= 0L) {
            writeStatus(output, 503, "Service Unavailable")
            return
        }
        val range = parseByteRange(rangeHeader, total)
        if (range == null) {
            output.write(
                buildString {
                    append("HTTP/1.1 416 Range Not Satisfiable\r\n")
                    append("Content-Range: bytes */$total\r\n")
                    append("Connection: close\r\n\r\n")
                }.toByteArray(),
            )
            output.flush()
            return
        }
        val (start, endInclusive) = range
        val length = endInclusive - start + 1
        val partial = rangeHeader != null
        output.write(
            buildString {
                append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                append("Content-Type: ${session.contentType}\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Content-Length: $length\r\n")
                if (partial) append("Content-Range: bytes $start-$endInclusive/$total\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(),
        )
        output.flush()
        // A HEAD is how some players probe for range support before they commit to playing.
        if (method == "HEAD") return

        var offset = start
        while (offset <= endInclusive) {
            if (!session.awaitAvailable(offset)) {
                // Nothing more is coming for this offset — a missing article, or the session was
                // closed. Ending the body is the only honest signal available at this point.
                Log.w(TAG, "Usenet stream stalled at $offset")
                return
            }
            val wanted = min(CHUNK_BYTES.toLong(), endInclusive - offset + 1).toInt()
            val chunk = session.readAt(offset, wanted)
            if (chunk.isEmpty()) continue
            try {
                output.write(chunk)
                output.flush()
            } catch (_: IOException) {
                // The player moved on — a seek, or playback stopped. Perfectly normal.
                return
            }
            offset += chunk.size
        }
    }

    private fun readLine(input: java.io.InputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val value = input.read()
            if (value == -1) return builder.takeIf { it.isNotEmpty() }?.toString()
            if (value == '\n'.code) return builder.toString()
            if (value != '\r'.code) builder.append(value.toChar())
        }
    }

    private fun writeStatus(output: OutputStream, code: Int, reason: String) {
        output.write("HTTP/1.1 $code $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        output.flush()
    }

    fun stop() {
        running.set(false)
        releaseAll()
        runCatching { serverSocket?.close() }
        serverSocket = null
        port = 0
    }
}

/**
 * Parses a `Range: bytes=…` header against a known total length.
 *
 * Returns the inclusive start/end pair, or null when the request cannot be satisfied. A missing
 * header means the whole file. Suffix ranges (`bytes=-500`) are supported because some players
 * use them to read a trailing index before anything else.
 */
internal fun parseByteRange(header: String?, totalLength: Long): Pair<Long, Long>? {
    if (totalLength <= 0L) return null
    val raw = header?.trim()?.removePrefix("bytes=")?.substringBefore(',')?.trim()
        ?: return 0L to totalLength - 1
    if (raw.isEmpty()) return 0L to totalLength - 1
    val startText = raw.substringBefore('-').trim()
    val endText = raw.substringAfter('-', "").trim()
    return when {
        startText.isEmpty() -> {
            val suffix = endText.toLongOrNull() ?: return null
            if (suffix <= 0L) return null
            val start = (totalLength - suffix).coerceAtLeast(0L)
            start to totalLength - 1
        }
        else -> {
            val start = startText.toLongOrNull() ?: return null
            if (start < 0 || start >= totalLength) return null
            val end = endText.toLongOrNull()?.coerceAtMost(totalLength - 1) ?: (totalLength - 1)
            if (end < start) return null
            start to end
        }
    }
}

/**
 * Turns a usenet stream into something the player can open.
 *
 * Downloads the NZB, picks the video file out of the post, opens the news server connections and
 * hands back a loopback URL. Everything after this point is [UsenetStreamServer]'s job.
 */
object UsenetPlayback {
    private val server = UsenetStreamServer()
    private val http = okhttp3.OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    @Volatile private var currentSessionId: String? = null

    /**
     * @param nzbUrl the NZB pointer from the add-on's stream
     * @param serverUris the `servers` array the same stream carried
     * @return a `http://127.0.0.1:…` URL to hand the player
     */
    fun open(context: Context, nzbUrl: String, serverUris: List<String>): String {
        val servers = serverUris.mapNotNull(::parseNntpServer)
        require(servers.isNotEmpty()) { "This usenet source did not name a news server to read from." }
        val nzb = downloadNzb(nzbUrl)
        val document = parseNzb(nzb)
        // Refused up front rather than assembled and handed over. A packed post used to produce
        // one archive part, which the player could make nothing of: it stalled for the length of
        // the range timeout and then failed with nothing to explain why. Saying so immediately is
        // the honest answer until unpacking exists.
        if (document.isPackedArchive()) {
            throw PackedUsenetPostException(
                "This usenet post is packed into archives, which StreamDek cannot play yet. Try another source.",
            )
        }
        val file = document.primaryVideoFile()
            ?: throw IOException("This usenet post contains no playable file.")

        currentSessionId?.let(server::release)
        val sessionId = java.util.UUID.randomUUID().toString().replace("-", "")
        val cacheFile = File(File(context.cacheDir, "usenet").apply { mkdirs() }, "$sessionId.part")
        val session = UsenetStreamSession(sessionId, servers, cacheFile)
        try {
            session.prepare(file)
        } catch (error: Throwable) {
            session.close()
            throw error
        }
        currentSessionId = sessionId
        return server.register(session)
    }

    /** Called when playback stops, so the connections close and the cache file goes away. */
    fun release() {
        currentSessionId?.let(server::release)
        currentSessionId = null
    }

    private fun downloadNzb(nzbUrl: String): String {
        // Some add-ons pad the URL with a trailing space; OkHttp rejects that outright.
        val request = okhttp3.Request.Builder()
            .url(nzbUrl.trim())
            .header("User-Agent", "StreamDek/1.0")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("The NZB could not be downloaded (HTTP ${response.code}).")
            return response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw IOException("The NZB was empty.")
        }
    }
}
