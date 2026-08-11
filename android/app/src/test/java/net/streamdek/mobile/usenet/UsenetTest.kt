package net.streamdek.mobile.usenet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsenetTest {
    // -- server URIs ---------------------------------------------------------------------------

    @Test
    fun `server uri carries host, port and credentials`() {
        val server = parseNntpServer("nntps://ZS6U24A6W937:secret@news.sunnyusenet.com:119")

        assertEquals("news.sunnyusenet.com", server?.host)
        assertEquals(119, server?.port)
        assertEquals("ZS6U24A6W937", server?.username)
        assertEquals("secret", server?.password)
        assertTrue(server?.requiresAuth == true)
    }

    @Test
    fun `credentials survive characters that had to be escaped`() {
        // A password with a '#' or '@' cannot appear raw in a URI, so add-ons percent-encode it.
        val server = parseNntpServer("nntps://user:K%21mempire19%23@news.example.com:563")

        assertEquals("K!mempire19#", server?.password)
        assertTrue(server?.useTls == true)
    }

    @Test
    fun `port 563 means TLS whatever the scheme claims`() {
        assertTrue(parseNntpServer("nntp://news.example.com:563")?.useTls == true)
        assertTrue(parseNntpServer("nntp://news.example.com:119")?.useTls == false)
        // The scheme is still honoured as a hint when the port says nothing.
        assertTrue(parseNntpServer("nntps://news.example.com")?.useTls == true)
    }

    @Test
    fun `default ports follow the scheme`() {
        assertEquals(119, parseNntpServer("nntp://news.example.com")?.port)
        assertEquals(563, parseNntpServer("nntps://news.example.com")?.port)
    }

    @Test
    fun `anything that is not a news server is rejected`() {
        assertNull(parseNntpServer(""))
        assertNull(parseNntpServer("https://example.com/file.nzb"))
        assertNull(parseNntpServer("nntp://"))
    }

    // -- NZB -----------------------------------------------------------------------------------

    private val sampleNzb = """
        <?xml version="1.0" encoding="iso-8859-1" ?>
        <nzb xmlns="http://www.newzbin.com/DTD/2003/nzb">
          <file subject="Some.Movie.2024 [01/12] - &quot;Some.Movie.2024.mkv&quot; yEnc (1/2)">
            <groups><group>alt.binaries.movies</group></groups>
            <segments>
              <segment bytes="760000" number="2">second@news</segment>
              <segment bytes="760000" number="1">&lt;first@news&gt;</segment>
            </segments>
          </file>
          <file subject="Some.Movie.2024 [02/12] - &quot;Some.Movie.2024.par2&quot; yEnc (1/1)">
            <segments><segment bytes="99000000" number="1">par@news</segment></segments>
          </file>
        </nzb>
    """.trimIndent()

    @Test
    fun `nzb segments are read in order with message ids unwrapped`() {
        val document = parseNzb(sampleNzb)

        assertEquals(2, document.files.size)
        val video = document.files.first()
        assertEquals(listOf(1, 2), video.segments.map { it.number })
        assertEquals("first@news", video.segments.first().messageId)
        assertEquals(listOf("alt.binaries.movies"), video.groups)
        assertEquals("Some.Movie.2024.mkv", video.filename)
    }

    @Test
    fun `the par2 set is not mistaken for the video, even when it is bigger`() {
        // The recovery file here is far larger than the video — picking on size alone gets it wrong.
        assertEquals("Some.Movie.2024.mkv", parseNzb(sampleNzb).primaryVideoFile()?.filename)
    }

    // -- yEnc ----------------------------------------------------------------------------------

    /** Encodes bytes the way a poster would, so the decoder is tested against the real format. */
    private fun yEncode(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        data.forEach { byte ->
            val shifted = ((byte.toInt() and 0xFF) + 42) and 0xFF
            // NUL, LF, CR and '=' must never appear raw in the stream.
            if (shifted == 0x00 || shifted == 0x0A || shifted == 0x0D || shifted == '='.code) {
                out.write('='.code)
                out.write((shifted + 64) and 0xFF)
            } else {
                out.write(shifted)
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `a single part article decodes back to its original bytes`() {
        val original = ByteArray(256) { it.toByte() }
        val body = java.io.ByteArrayOutputStream().apply {
            write("=ybegin line=128 size=256 name=Some.Movie.mkv\r\n".toByteArray())
            write(yEncode(original))
            write("\r\n=yend size=256\r\n".toByteArray())
        }.toByteArray()

        val part = decodeYEnc(body)

        assertEquals("Some.Movie.mkv", part?.name)
        assertEquals(256L, part?.totalSize)
        assertEquals(0L, part?.begin)
        assertTrue(original.contentEquals(part?.data))
    }

    @Test
    fun `a part knows where it belongs from its ypart header`() {
        val original = ByteArray(64) { (it * 3).toByte() }
        val body = java.io.ByteArrayOutputStream().apply {
            write("=ybegin part=2 line=128 size=1000 name=Some.Movie.mkv\r\n".toByteArray())
            write("=ypart begin=501 end=564\r\n".toByteArray())
            write(yEncode(original))
            write("\r\n=yend size=64 part=2\r\n".toByteArray())
        }.toByteArray()

        val part = decodeYEnc(body)

        // yEnc part offsets are 1-based; the assembled file is not.
        assertEquals(500L, part?.begin)
        assertEquals(1000L, part?.totalSize)
        assertTrue(original.contentEquals(part?.data))
    }

    @Test
    fun `a body with no yEnc payload decodes to nothing`() {
        assertNull(decodeYEnc("just some text\r\n".toByteArray()))
    }

    // -- range requests ------------------------------------------------------------------------

    @Test
    fun `range headers resolve to inclusive byte pairs`() {
        assertEquals(0L to 999L, parseByteRange(null, 1000))
        assertEquals(0L to 999L, parseByteRange("bytes=0-", 1000))
        assertEquals(100L to 199L, parseByteRange("bytes=100-199", 1000))
        // A suffix range: the last 500 bytes, which is how some players read a trailing index.
        assertEquals(500L to 999L, parseByteRange("bytes=-500", 1000))
        // An end past the file is clamped rather than refused.
        assertEquals(900L to 999L, parseByteRange("bytes=900-5000", 1000))
    }

    @Test
    fun `unsatisfiable ranges are refused`() {
        assertNull(parseByteRange("bytes=1000-", 1000))
        assertNull(parseByteRange("bytes=500-499", 1000))
        assertNull(parseByteRange("bytes=0-", 0))
    }
}
