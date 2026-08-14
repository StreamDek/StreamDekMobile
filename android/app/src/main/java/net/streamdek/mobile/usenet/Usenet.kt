package net.streamdek.mobile.usenet

import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * On-device usenet streaming.
 *
 * A usenet result from an add-on is not a playable link: it is a pointer to an NZB (an index of
 * article ids) plus the news servers those articles live on. Turning that into video means doing
 * what a newsreader does — connect to the server, pull each article, yEnc-decode it, and lay the
 * pieces back down in order — and then serving the result to the player over a loopback HTTP
 * socket so it can seek.
 *
 * All of it happens on the device. Nothing about a usenet stream, including the news server
 * credentials, ever reaches a StreamDek server.
 */

/**
 * The post is a split archive rather than a video this assembler can read.
 *
 * Typed so the caller can tell it apart from a source that failed: nothing is wrong with the
 * post or the connection, it is simply a shape not supported yet, and the viewer should be told
 * to pick another source rather than to try again.
 */
class PackedUsenetPostException(message: String) : IllegalStateException(message)

/** A news server to pull articles from, as parsed out of an add-on's `servers` entry. */
data class NntpServer(
    val host: String,
    val port: Int,
    val useTls: Boolean,
    val username: String? = null,
    val password: String? = null,
) {
    val requiresAuth: Boolean get() = !username.isNullOrBlank()
}

/**
 * Parses `nntps://user:pass@news.example.com:563`.
 *
 * Add-ons are loose about the scheme: the servers AIOStreams hands out say `nntps` while pointing
 * at 119, which is the plaintext port. The scheme is taken as a hint rather than a promise —
 * [NntpConnection] tries TLS first when it is implied and falls back — but port 563 always means
 * TLS whatever the scheme says. Credentials are percent-decoded, since a password with an `@` or
 * a `#` in it has to be escaped to survive the URI at all.
 */
fun parseNntpServer(raw: String): NntpServer? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val normalized = if (trimmed.contains("://")) trimmed else "nntp://$trimmed"
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
    if (scheme != "nntp" && scheme != "nntps" && scheme != "snews") return null
    val port = uri.port.takeIf { it > 0 } ?: if (scheme == "nntp") 119 else 563
    val userInfo = uri.userInfo.orEmpty()
    val username = userInfo.substringBefore(':', "").takeIf { it.isNotBlank() }?.let(::decodeUriComponent)
    val password = userInfo.substringAfter(':', "").takeIf { it.isNotBlank() }?.let(::decodeUriComponent)
    return NntpServer(
        host = host,
        port = port,
        useTls = port == 563 || scheme == "nntps" || scheme == "snews",
        username = username,
        password = password,
    )
}

private fun decodeUriComponent(value: String): String =
    runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

// -- NZB ---------------------------------------------------------------------------------------

/** One article of a file: [messageId] is what the news server is asked for. */
data class NzbSegment(val number: Int, val bytes: Long, val messageId: String)

data class NzbFile(
    val subject: String,
    val groups: List<String>,
    val segments: List<NzbSegment>,
) {
    /** Sum of the *encoded* article sizes — an upper bound, roughly 3% above the real file. */
    val encodedBytes: Long get() = segments.sumOf { it.bytes }

    /** The filename an NZB subject traditionally quotes: `... "Some.Movie.mkv" yEnc (1/420)`. */
    val filename: String? get() = Regex("\"([^\"]+)\"").find(subject)?.groupValues?.getOrNull(1)
}

data class NzbDocument(val files: List<NzbFile>) {
    /**
     * The file to play.
     *
     * A post carries more than the video — par2 recovery blocks, an nfo, sample clips — so the
     * candidate is the largest file that does not look like one of those. Falling back to the
     * plain largest keeps obfuscated posts (whose names say nothing) working.
     */
    fun primaryVideoFile(): NzbFile? {
        val playable = files.filter { file ->
            val name = (file.filename ?: file.subject).lowercase(Locale.US)
            val isSupportingFile = SUPPORTING_FILE_MARKERS.any { name.contains(it) }
            val isSample = Regex("\\bsample\\b").containsMatchIn(name)
            !isSupportingFile && !isSample
        }
        return (playable.ifEmpty { files }).maxByOrNull { it.encodedBytes }
    }

    /**
     * Whether this post is an archive split across its files rather than the video itself.
     *
     * The overwhelming majority of usenet posts are packed: the release is RAR'd and spread over
     * dozens or hundreds of parts, often with the names obfuscated to bare numbers —
     * `7a7dc041….10`, `….101`, and so on. Nothing here unpacks them, and picking the largest part
     * and playing it produces a fragment of an archive that no player can read.
     *
     * Judged by what is *not* there rather than by naming every archive convention: a post this
     * assembler can play contains a video file, so a candidate that is not one means packed. That
     * catches `.rar`, `.r00`, `.001` and the numeric obfuscation together, and cannot be fooled by
     * a scheme nobody has thought of yet.
     */
    fun isPackedArchive(): Boolean = primaryVideoFile()?.let { !isPlayableVideoName(it.filename ?: it.subject) } ?: false

    companion object {
        private val SUPPORTING_FILE_MARKERS = listOf(".par2", ".nfo", ".sfv", ".srr", ".jpg", ".png", ".txt")
    }
}

/** Video containers this assembler can hand a player directly. */
private val PLAYABLE_VIDEO_EXTENSIONS = listOf(".mkv", ".mp4", ".m4v", ".avi", ".mov", ".ts", ".webm", ".wmv", ".m2ts")

internal fun isPlayableVideoName(name: String): Boolean {
    val lower = name.lowercase(Locale.US).trim()
    return PLAYABLE_VIDEO_EXTENSIONS.any { lower.endsWith(it) }
}

/**
 * Reads an NZB. Namespaces are ignored deliberately: real-world NZBs are inconsistent about
 * declaring the newzbin namespace, and matching on the local name works for all of them.
 */
fun parseNzb(xml: String): NzbDocument {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        // An NZB is a file fetched from a third party; it has no business resolving entities.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        isExpandEntityReferences = false
    }
    val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    val fileNodes = document.getElementsByTagName("file")
    val files = buildList {
        for (index in 0 until fileNodes.length) {
            val fileElement = fileNodes.item(index) as? Element ?: continue
            val segmentNodes = fileElement.getElementsByTagName("segment")
            val segments = buildList {
                for (segmentIndex in 0 until segmentNodes.length) {
                    val segment = segmentNodes.item(segmentIndex) as? Element ?: continue
                    val messageId = segment.textContent?.trim()?.trim('<', '>').orEmpty()
                    if (messageId.isBlank()) continue
                    add(
                        NzbSegment(
                            number = segment.getAttribute("number").toIntOrNull() ?: (segmentIndex + 1),
                            bytes = segment.getAttribute("bytes").toLongOrNull() ?: 0L,
                            messageId = messageId,
                        ),
                    )
                }
            }.sortedBy { it.number }
            if (segments.isEmpty()) continue
            val groupNodes = fileElement.getElementsByTagName("group")
            val groups = buildList {
                for (groupIndex in 0 until groupNodes.length) {
                    groupNodes.item(groupIndex)?.textContent?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            add(NzbFile(subject = fileElement.getAttribute("subject").orEmpty(), groups = groups, segments = segments))
        }
    }
    return NzbDocument(files)
}

// -- yEnc --------------------------------------------------------------------------------------

/**
 * One decoded article.
 *
 * [begin] is the zero-based offset the bytes belong at in the assembled file, and [totalSize] is
 * the whole file's length as the poster declared it — the only way to know how big the result will
 * be before every article has been pulled.
 */
data class YEncPart(
    val name: String?,
    val begin: Long,
    val totalSize: Long?,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is YEncPart && name == other.name && begin == other.begin && totalSize == other.totalSize && data.contentEquals(other.data))

    override fun hashCode(): Int = (((name?.hashCode() ?: 0) * 31 + begin.hashCode()) * 31 + (totalSize?.hashCode() ?: 0)) * 31 + data.contentHashCode()
}

/**
 * Decodes the yEnc payload of one article body.
 *
 * yEnc shifts every byte by 42 and escapes the handful that would otherwise break the wire format
 * with `=` plus a further shift of 64. The header lines carry where the part belongs: `=ypart`
 * gives 1-based inclusive offsets for a multi-part file, and a single-part file simply starts at
 * the beginning. Returns null when the body carries no yEnc data at all.
 */
fun decodeYEnc(body: ByteArray): YEncPart? {
    var name: String? = null
    var totalSize: Long? = null
    var begin = 0L
    var sawBegin = false
    val output = java.io.ByteArrayOutputStream(body.size)

    var index = 0
    var escaped = false
    var inData = false
    while (index < body.size) {
        val lineEnd = nextLineEnd(body, index)
        val lineLength = lineEnd - index
        if (startsWith(body, index, "=y")) {
            val header = String(body, index, lineLength, Charsets.ISO_8859_1)
            when {
                header.startsWith("=ybegin") -> {
                    sawBegin = true
                    inData = !header.contains("part=")
                    name = headerValue(header, "name")
                    totalSize = headerValue(header, "size")?.toLongOrNull()
                }
                header.startsWith("=ypart") -> {
                    // yEnc part offsets are 1-based and inclusive; the assembled file is not.
                    begin = (headerValue(header, "begin")?.toLongOrNull() ?: 1L) - 1L
                    inData = true
                }
                header.startsWith("=yend") -> inData = false
            }
            index = skipLineBreak(body, lineEnd)
            continue
        }
        if (!inData) {
            index = skipLineBreak(body, lineEnd)
            continue
        }
        var cursor = index
        while (cursor < lineEnd) {
            val raw = body[cursor].toInt() and 0xFF
            when {
                escaped -> {
                    output.write((raw - 64 - 42) and 0xFF)
                    escaped = false
                }
                raw == '='.code -> escaped = true
                else -> output.write((raw - 42) and 0xFF)
            }
            cursor++
        }
        index = skipLineBreak(body, lineEnd)
    }
    if (!sawBegin) return null
    return YEncPart(name = name, begin = begin, totalSize = totalSize, data = output.toByteArray())
}

private fun nextLineEnd(body: ByteArray, from: Int): Int {
    var index = from
    while (index < body.size && body[index] != '\n'.code.toByte() && body[index] != '\r'.code.toByte()) index++
    return index
}

private fun skipLineBreak(body: ByteArray, from: Int): Int {
    var index = from
    if (index < body.size && body[index] == '\r'.code.toByte()) index++
    if (index < body.size && body[index] == '\n'.code.toByte()) index++
    return index
}

private fun startsWith(body: ByteArray, offset: Int, prefix: String): Boolean {
    if (offset + prefix.length > body.size) return false
    for (index in prefix.indices) {
        if (body[offset + index] != prefix[index].code.toByte()) return false
    }
    return true
}

/** Pulls `key=value` out of a yEnc header line. `name=` runs to the end of the line by spec. */
private fun headerValue(header: String, key: String): String? {
    val marker = "$key="
    val start = header.indexOf(marker).takeIf { it >= 0 } ?: return null
    val valueStart = start + marker.length
    if (key == "name") return header.substring(valueStart).trim().takeIf { it.isNotEmpty() }
    val end = header.indexOf(' ', valueStart).takeIf { it >= 0 } ?: header.length
    return header.substring(valueStart, end).trim().takeIf { it.isNotEmpty() }
}
