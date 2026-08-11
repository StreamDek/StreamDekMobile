package net.streamdek.mobile.usenet

import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

private const val TAG = "StreamDekUsenet"

/** Articles in flight at once. News accounts cap connections; this stays well inside typical limits. */
private const val DEFAULT_CONNECTIONS = 6

/** How far ahead of the play head to keep pulling before idling. Roughly 128 MB of video. */
private const val READ_AHEAD_SEGMENTS = 200

/** How long a range request will wait for its bytes before giving up on the source. */
private const val RANGE_WAIT_TIMEOUT_MS = 90_000L

/**
 * One usenet download, assembled on disk and readable while it is still filling in.
 *
 * Segments are pulled in order from a moving head rather than all at once, so playback can start
 * on the first few megabytes; seeking moves the head instead of waiting for everything in between.
 * Each decoded article is written straight to its own offset in a sparse file, which is what makes
 * out-of-order completion and resume-after-seek work without holding anything in memory.
 *
 * Everything is local: the articles come from the news server the add-on named, straight to this
 * device, and the assembled file never leaves it.
 */
class UsenetStreamSession(
    val id: String,
    private val servers: List<NntpServer>,
    private val cacheFile: File,
    connections: Int = DEFAULT_CONNECTIONS,
) {
    /** Byte range one article covers in the assembled file. */
    private data class Placement(val segment: NzbSegment, val begin: Long, val endExclusive: Long)

    private val pool = NntpConnectionPool(servers, connections)
    private val workers = Executors.newFixedThreadPool(connections.coerceAtLeast(1))
    private val closed = AtomicBoolean(false)
    private val completed = ConcurrentHashMap<Int, Placement>()
    private val inFlight = ConcurrentHashMap<Int, Boolean>()
    private val failedSegments = ConcurrentHashMap<Int, Boolean>()
    private val lock = Object()

    private lateinit var placements: List<Placement>
    private var randomAccess: RandomAccessFile? = null

    /** Total length of the assembled file, from the first article's yEnc header. */
    @Volatile var totalBytes: Long = 0L
        private set

    @Volatile var filename: String? = null
        private set

    /** Index of the next segment to schedule. Moves backwards when the viewer seeks. */
    private val head = AtomicInteger(0)

    val contentType: String get() = guessContentType(filename)

    /**
     * Pulls the first article to learn the file's real length, lays out where every other article
     * belongs, and starts filling in from the front. Returns the total size in bytes.
     */
    fun prepare(file: NzbFile): Long {
        require(file.segments.isNotEmpty()) { "This usenet post has no articles." }
        val first = pool.withConnection { it.body(file.segments.first().messageId) }
        val decoded = decodeYEnc(first) ?: throw IOException("The first article is not yEnc-encoded.")
        val declaredSize = decoded.totalSize
            ?: throw IOException("This usenet post does not declare a file size.")
        totalBytes = declaredSize
        filename = decoded.name

        // Article n's offset is only known from its own =ypart header, so before anything is
        // downloaded the layout is estimated from the first part's length — every part of a post
        // is the same size bar the last. Each article corrects its own placement as it lands.
        val partSize = max(1L, decoded.data.size.toLong())
        placements = file.segments.mapIndexed { index, segment ->
            val begin = partSize * index
            Placement(segment, begin, min(begin + partSize, declaredSize))
        }

        cacheFile.parentFile?.mkdirs()
        randomAccess = RandomAccessFile(cacheFile, "rw").apply { setLength(declaredSize) }
        writePart(0, decoded)
        head.set(1)
        Log.i(TAG, "Prepared usenet session $id: ${file.segments.size} articles, $declaredSize bytes")
        pump()
        return declaredSize
    }

    /** Schedules as many articles as the read-ahead window allows. */
    private fun pump() {
        if (closed.get()) return
        val start = head.get()
        var scheduled = 0
        var index = start
        while (index < placements.size && scheduled < READ_AHEAD_SEGMENTS) {
            if (completed.containsKey(index) || failedSegments.containsKey(index)) { index++; continue }
            if (inFlight.putIfAbsent(index, true) == null) {
                val target = index
                workers.execute { download(target) }
                scheduled++
            }
            index++
        }
    }

    private fun download(index: Int) {
        if (closed.get()) return
        val placement = placements.getOrNull(index) ?: return
        try {
            val body = pool.withConnection { it.body(placement.segment.messageId) }
            val decoded = decodeYEnc(body)
            if (decoded == null) {
                failedSegments[index] = true
                Log.w(TAG, "Article ${placement.segment.number} carried no yEnc payload")
            } else {
                writePart(index, decoded)
            }
        } catch (error: ArticleNotFoundException) {
            // A missing article is a hole in the file, not the end of the download. Without par2
            // there is nothing to rebuild it from, so the gap stays and playback may glitch there.
            failedSegments[index] = true
            Log.w(TAG, "Missing article for segment ${placement.segment.number}")
        } catch (error: Throwable) {
            failedSegments[index] = true
            Log.w(TAG, "Segment ${placement.segment.number} failed", error)
        } finally {
            inFlight.remove(index)
            synchronized(lock) { lock.notifyAll() }
            if (!closed.get()) pump()
        }
    }

    /** Writes one decoded article where its own header says it belongs. */
    private fun writePart(index: Int, part: YEncPart) {
        val placement = placements.getOrNull(index) ?: return
        // The =ypart header is authoritative; the estimate from prepare() only orders the work.
        val begin = if (part.begin > 0 || index == 0) part.begin else placement.begin
        val file = randomAccess ?: return
        synchronized(lock) {
            file.seek(begin)
            file.write(part.data)
            completed[index] = placement.copy(begin = begin, endExclusive = begin + part.data.size)
            lock.notifyAll()
        }
    }

    /** How far from [from] the file is continuously present on disk. */
    private fun contiguousEndFrom(from: Long): Long {
        val ranges = completed.values.sortedBy { it.begin }
        var reach = from
        for (range in ranges) {
            if (range.begin > reach) break
            reach = max(reach, range.endExclusive)
        }
        return reach
    }

    /**
     * Blocks until [offset] is readable, moving the download head there first if the viewer has
     * seeked past what has been fetched. Returns false when the wait timed out or the gap is a
     * segment that will never arrive.
     */
    fun awaitAvailable(offset: Long): Boolean {
        if (offset >= totalBytes) return false
        val deadline = System.currentTimeMillis() + RANGE_WAIT_TIMEOUT_MS
        seekTo(offset)
        synchronized(lock) {
            while (!closed.get() && contiguousEndFrom(offset) <= offset) {
                if (segmentIndexFor(offset)?.let { failedSegments.containsKey(it) } == true) return false
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return false
                lock.wait(min(remaining, 500L))
            }
        }
        return !closed.get()
    }

    private fun segmentIndexFor(offset: Long): Int? =
        placements.indexOfFirst { offset >= it.begin && offset < it.endExclusive }.takeIf { it >= 0 }

    /** Points the download head at whichever article covers [offset]. */
    fun seekTo(offset: Long) {
        val index = segmentIndexFor(offset) ?: return
        if (completed.containsKey(index)) return
        head.set(index)
        pump()
    }

    /**
     * Reads what is already on disk at [offset], up to [maxLength] and never past the end of the
     * contiguous run. Returns an empty array when nothing is available yet.
     */
    fun readAt(offset: Long, maxLength: Int): ByteArray {
        val file = randomAccess ?: return ByteArray(0)
        synchronized(lock) {
            val reach = contiguousEndFrom(offset)
            val length = min(maxLength.toLong(), reach - offset).toInt()
            if (length <= 0) return ByteArray(0)
            val buffer = ByteArray(length)
            file.seek(offset)
            file.readFully(buffer)
            return buffer
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) { lock.notifyAll() }
        workers.shutdownNow()
        runCatching { workers.awaitTermination(2, TimeUnit.SECONDS) }
        runCatching { pool.close() }
        synchronized(lock) { runCatching { randomAccess?.close() } }
        randomAccess = null
        runCatching { cacheFile.delete() }
        Log.i(TAG, "Closed usenet session $id")
    }
}

internal fun guessContentType(filename: String?): String = when (filename?.substringAfterLast('.', "")?.lowercase()) {
    "mp4", "m4v" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "avi" -> "video/x-msvideo"
    "ts" -> "video/mp2t"
    "webm" -> "video/webm"
    else -> "video/mp4"
}
