package net.streamdek.mobile.usenet

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 45_000

/** Article not carried by this server — common, and not worth failing the whole download over. */
class ArticleNotFoundException(messageId: String) : IOException("Article not available: $messageId")

/**
 * One connection to a news server, speaking enough NNTP to pull article bodies.
 *
 * Deliberately raw rather than built on a text reader: yEnc bodies are binary, and decoding them
 * as characters corrupts every byte above 127. Everything here works on bytes and only interprets
 * ASCII where the protocol guarantees it — status lines and the dot terminator.
 *
 * Not thread-safe. [NntpConnectionPool] hands each caller its own.
 */
class NntpConnection(private val server: NntpServer) : Closeable {
    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: OutputStream? = null

    val isOpen: Boolean get() = socket?.isConnected == true && socket?.isClosed == false

    fun connect() {
        close()
        // The scheme an add-on hands out is a hint, not a promise: AIOStreams advertises `nntps`
        // on port 119, which is the plaintext port. Try what was asked for, then the other way.
        val attempts = if (server.useTls) listOf(true, false) else listOf(false, true)
        var lastFailure: Throwable? = null
        for (useTls in attempts) {
            runCatching { openSocket(useTls) }
                .onSuccess { return }
                .onFailure { failure ->
                    lastFailure = failure
                    close()
                }
        }
        throw lastFailure ?: IOException("Could not reach ${server.host}:${server.port}")
    }

    private fun openSocket(useTls: Boolean) {
        val plain = Socket()
        plain.connect(InetSocketAddress(server.host, server.port), CONNECT_TIMEOUT_MS)
        plain.soTimeout = READ_TIMEOUT_MS
        plain.tcpNoDelay = true
        val active = if (useTls) {
            (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(plain, server.host, server.port, true)
                .also { (it as javax.net.ssl.SSLSocket).startHandshake() }
        } else {
            plain
        }
        socket = active
        input = BufferedInputStream(active.getInputStream(), 64 * 1024)
        output = active.getOutputStream()

        // 200 = posting allowed, 201 = read-only. Either is fine; we only ever read.
        val greeting = readStatusLine()
        if (!greeting.startsWith("200") && !greeting.startsWith("201")) {
            throw IOException("News server refused the connection: $greeting")
        }
        if (server.requiresAuth) authenticate()
    }

    private fun authenticate() {
        val userReply = command("AUTHINFO USER ${server.username}")
        // 381 asks for the password; 281 means the username alone was enough.
        if (userReply.startsWith("281")) return
        if (!userReply.startsWith("381")) throw IOException("News server rejected the username: $userReply")
        val passReply = command("AUTHINFO PASS ${server.password.orEmpty()}")
        if (!passReply.startsWith("281")) throw IOException("News server rejected the credentials: $passReply")
    }

    /**
     * The raw body of one article, still yEnc-encoded.
     *
     * BODY is used rather than ARTICLE so the headers never reach the decoder — they are of no
     * use here and some servers wrap them in ways that confuse a naive yEnc scan.
     */
    fun body(messageId: String): ByteArray {
        val normalized = if (messageId.startsWith("<")) messageId else "<$messageId>"
        val reply = command("BODY $normalized")
        if (reply.startsWith("222")) return readDotTerminatedBody()
        if (reply.startsWith("430") || reply.startsWith("423") || reply.startsWith("420")) {
            throw ArticleNotFoundException(messageId)
        }
        throw IOException("Unexpected reply to BODY: $reply")
    }

    private fun command(line: String): String {
        val stream = output ?: throw IOException("Not connected")
        stream.write((line + "\r\n").toByteArray(Charsets.ISO_8859_1))
        stream.flush()
        return readStatusLine()
    }

    private fun readStatusLine(): String {
        val stream = input ?: throw IOException("Not connected")
        val buffer = ByteArrayOutputStream(64)
        while (true) {
            val value = stream.read()
            if (value == -1) throw IOException("News server closed the connection")
            if (value == '\n'.code) break
            if (value != '\r'.code) buffer.write(value)
        }
        return buffer.toString("ISO-8859-1")
    }

    /**
     * Reads until the lone "." that ends a multi-line reply, undoing the dot-stuffing the
     * protocol applies to any body line that happens to start with one.
     */
    private fun readDotTerminatedBody(): ByteArray {
        val stream = input ?: throw IOException("Not connected")
        val body = ByteArrayOutputStream(768 * 1024)
        val line = ByteArrayOutputStream(1024)
        while (true) {
            val value = stream.read()
            if (value == -1) throw IOException("News server closed the connection mid-article")
            if (value != '\n'.code) {
                if (value != '\r'.code) line.write(value)
                continue
            }
            val bytes = line.toByteArray()
            line.reset()
            if (bytes.size == 1 && bytes[0] == '.'.code.toByte()) break
            if (bytes.isNotEmpty() && bytes[0] == '.'.code.toByte()) {
                body.write(bytes, 1, bytes.size - 1)
            } else {
                body.write(bytes)
            }
            body.write('\r'.code)
            body.write('\n'.code)
        }
        return body.toByteArray()
    }

    override fun close() {
        runCatching { output?.write("QUIT\r\n".toByteArray(Charsets.ISO_8859_1)) }
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }
}

/**
 * A fixed set of connections shared by the download workers.
 *
 * News providers cap concurrent connections per account and drop everything over the limit, so
 * this never opens more than [size] at once and reuses them across articles — reconnecting costs
 * a TLS handshake and an auth round-trip that would otherwise be paid per segment.
 */
class NntpConnectionPool(private val servers: List<NntpServer>, private val size: Int) : Closeable {
    private val available = java.util.concurrent.ArrayBlockingQueue<NntpConnection>(size.coerceAtLeast(1))
    private val all = java.util.Collections.synchronizedList(mutableListOf<NntpConnection>())
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        repeat(size.coerceAtLeast(1)) { available.put(NntpConnection(servers.first())) }
    }

    /**
     * Runs [block] with a live connection, reconnecting first if the last use left it dead.
     * A connection that fails mid-block is replaced rather than handed back broken.
     */
    fun <T> withConnection(block: (NntpConnection) -> T): T {
        check(!closed.get()) { "This usenet session has been closed." }
        var connection = available.take()
        try {
            if (!connection.isOpen) {
                connection.connect()
                if (!all.contains(connection)) all.add(connection)
            }
            return block(connection)
        } catch (error: ArticleNotFoundException) {
            throw error
        } catch (error: Throwable) {
            runCatching { connection.close() }
            all.remove(connection)
            connection = NntpConnection(servers.first())
            throw error
        } finally {
            if (!closed.get()) available.offer(connection) else runCatching { connection.close() }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val drained = mutableListOf<NntpConnection>()
        available.drainTo(drained)
        (drained + all).forEach { runCatching { it.close() } }
        all.clear()
        Log.i("StreamDekUsenet", "Closed news server connections")
    }
}
