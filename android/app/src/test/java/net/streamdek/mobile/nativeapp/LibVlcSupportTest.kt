package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibVlcSupportTest {
  @Test
  fun `user agent and referer are the only headers libvlc sends`() {
    assertEquals(emptyList<String>(), headersLibVlcCannotSend(null))
    assertEquals(emptyList<String>(), headersLibVlcCannotSend(mapOf("User-Agent" to "x", "Referer" to "https://a")))
    assertEquals(listOf("Authorization"), headersLibVlcCannotSend(mapOf("User-Agent" to "x", "Authorization" to "Bearer t")))
    assertEquals(listOf("Origin", "Cookie", "X-Token"), headersLibVlcCannotSend(mapOf("Origin" to "https://a", "Cookie" to "a=b", "X-Token" to "1")))
  }

  @Test
  fun `headers that do not decide whether a source opens are not blockers`() {
    assertEquals(emptyList<String>(), headersLibVlcCannotSend(mapOf("Accept" to "*/*", "Range" to "bytes=0-", "Accept-Language" to "en")))
    assertEquals(emptyList<String>(), headersLibVlcCannotSend(mapOf("Authorization" to " ")))
  }

  @Test
  fun `a source is blocked for libvlc by clearkey or by its headers`() {
    assertNull(libVlcBlocker(mapOf("User-Agent" to "x"), clearKeyProtected = false))
    assertEquals(LibVlcBlocker.Headers, libVlcBlocker(mapOf("Cookie" to "a=b"), clearKeyProtected = false))
    assertEquals(LibVlcBlocker.ClearKey, libVlcBlocker(null, clearKeyProtected = true))
  }

  @Test
  fun `half a stream is a problem only when the other half is running`() {
    fun check(audio: Boolean, selected: Boolean, video: Boolean, da: Long, pa: Long, dv: Long, dp: Long) =
      libVlcCompatibilityProblem(audio, selected, video, da, pa, dv, dp)
    assertNull(check(true, true, true, 0, 0, 0, 0))
    assertNull(check(true, true, true, 90, 90, 120, 118))
    assertEquals(LibVlcCompatibilityProblem.NoAudio, check(true, true, true, 0, 0, 120, 118))
    assertEquals(LibVlcCompatibilityProblem.NoVideo, check(true, true, true, 90, 90, 0, 0))
    // No audio track at all, or audio switched off by the viewer, is not a fault.
    assertNull(check(false, false, true, 0, 0, 120, 118))
    assertNull(check(true, false, true, 0, 0, 120, 118))
    // An audio-only source is not missing its video.
    assertNull(check(true, true, false, 90, 90, 0, 0))
  }

  @Test
  fun `dolby vision sample entries are recognised and plain hevc is not`() {
    fun cc(s: String) = s[0].code or (s[1].code shl 8) or (s[2].code shl 16) or (s[3].code shl 24)
    assertTrue(isDolbyVisionOnlyFourcc(cc("dvhe")))
    assertTrue(isDolbyVisionOnlyFourcc(cc("dvh1")))
    assertFalse(isDolbyVisionOnlyFourcc(cc("hvc1")))
    assertFalse(isDolbyVisionOnlyFourcc(cc("hev1")))
    assertFalse(isDolbyVisionOnlyFourcc(0))
  }

  @Test
  fun `codec descriptions become the keys the info panel names`() {
    assertEquals("h264", libVlcCodecKey("H264 - MPEG-4 AVC (part 10)"))
    assertEquals("hevc", libVlcCodecKey("MPEG-H Part2/HEVC (H.265)"))
    assertEquals("eac3", libVlcCodecKey("A/52 B Audio (aka E-AC3)"))
    assertEquals("ac3", libVlcCodecKey("A52 Audio (aka AC3)"))
    assertEquals("aac", libVlcCodecKey("MPEG AAC Audio"))
    assertEquals("Something Else", libVlcCodecKey("Something Else"))
    assertNull(libVlcCodecKey(" "))
  }

  @Test
  fun `profiles levels and bit depth read the way they are usually written`() {
    assertEquals("High@L4.1", libVlcProfileName("h264", 100, 41))
    assertEquals("Main 10@L5.1", libVlcProfileName("hevc", 2, 153))
    assertEquals("Main", libVlcProfileName("hevc", 1, 0))
    assertNull(libVlcProfileName("av1", 0, 0))
    assertEquals(10, libVlcBitDepth("hevc", 2))
    assertEquals(8, libVlcBitDepth("h264", 100))
    assertNull(libVlcBitDepth("hevc", 4))
  }

  @Test
  fun `sources on this device or network start sooner than remote ones`() {
    assertEquals(800, libVlcNetworkCachingMs("http://127.0.0.1:8090/stream", live = false))
    assertEquals(800, libVlcNetworkCachingMs("http://192.168.0.10:32400/library/parts/1/file.mkv", live = false))
    assertEquals(800, libVlcNetworkCachingMs("http://user:pass@172.20.1.4/a.mkv", live = false))
    assertEquals(1_500, libVlcNetworkCachingMs("https://cdn.example.com/a.mkv", live = false))
    assertEquals(1_500, libVlcNetworkCachingMs("https://172.99.1.1/a.m3u8", live = true))
  }
}
