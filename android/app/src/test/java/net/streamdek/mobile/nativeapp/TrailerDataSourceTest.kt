package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerDataSourceTest {
  private val mediaUrl =
    "https://rr3---sn-ajaig5-5a.googlevideo.com/videoplayback?expire=1786499547&itag=137" +
      "&source=youtube&requiressl=yes&mime=video%2Fmp4&rqh=1&gir=yes&clen=23516357&dur=129.462"

  @Test
  fun `media urls are fetched in bounded spans`() {
    // The whole reason this exists: googlevideo answers 403 to an open-ended request for one of
    // these, which is exactly what a progressive media source issues.
    assertTrue(requiresChunkedRange(mediaUrl))
    assertEquals(23516357L, contentLengthOf(mediaUrl))
  }

  @Test
  fun `leaves everything that is not a googlevideo media url alone`() {
    assertFalse(requiresChunkedRange("https://cdn.example.com/trailer.mp4"))
    assertFalse(requiresChunkedRange("https://streamdek.net/videoplayback?clen=100"))
    // A host that merely ends in something similar must not match.
    assertFalse(requiresChunkedRange("https://notgooglevideo.com.evil.test/videoplayback?clen=1"))
    assertNull(contentLengthOf("https://cdn.example.com/trailer.mp4"))
  }

  @Test
  fun `leaves hls segments alone`() {
    // HLS segments sit on the same hosts but carry their span in the path and are small enough to
    // be served whole, so rewriting them would break a path that already works.
    assertFalse(
      requiresChunkedRange(
        "https://rr3---sn-ajaig5-5a.googlevideo.com/videoplayback/range/0-524287/itag/232?clen=999",
      ),
    )
  }

  @Test
  fun `does not add a second range to a url that already carries one`() {
    assertFalse(requiresChunkedRange("$mediaUrl&range=0-8388607"))
  }

  @Test
  fun `ignores a size it cannot use`() {
    assertNull(contentLengthOf("https://x.googlevideo.com/videoplayback?clen=0"))
    assertNull(contentLengthOf("https://x.googlevideo.com/videoplayback?clen=abc"))
    // `clen` must match on the whole parameter name, not as the tail of another one.
    assertNull(contentLengthOf("https://x.googlevideo.com/videoplayback?xclen=42"))
  }
}
