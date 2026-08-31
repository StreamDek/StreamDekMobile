package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two things that decided, silently, whether a chosen subtitle rendered at all.
 *
 * Both were previously read from the URL, which for these sources says nothing: every file was
 * saved as .srt and read as UTF-8 whatever it actually was.
 */
class SubtitleFileTest {

  @Test
  fun `subtitle delay shifts the cue timeline without moving video playback`() {
    assertEquals(85_000_000L, delayedSubtitlePositionUs(100_000L, 15.0))
    assertEquals(115_000_000L, delayedSubtitlePositionUs(100_000L, -15.0))
    assertEquals(100_000_000L, delayedSubtitlePositionUs(100_000L, 0.0))
    assertEquals(85_000_000L, delayedSubtitlePositionUs(100_000L, 30.0))
  }

  @Test
  fun `a webvtt file is recognised as webvtt`() {
    val body = "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nHello\n"
    assertEquals("vtt", subtitleExtensionFor(body))
  }

  @Test
  fun `an advanced substation file is recognised by its events section`() {
    val body = "[Script Info]\nTitle: x\n\n[Events]\nFormat: Layer, Start, End, Text\nDialogue: 0,0:00:01.00,0:00:03.00,Hi\n"
    assertEquals("ass", subtitleExtensionFor(body))
  }

  @Test
  fun `a ttml file is recognised by its root element`() {
    val body = "<?xml version=\"1.0\"?>\n<tt xmlns=\"http://www.w3.org/ns/ttml\"><body/></tt>"
    assertEquals("ttml", subtitleExtensionFor(body))
  }

  @Test
  fun `anything else is treated as subrip`() {
    val body = "1\n00:00:01,000 --> 00:00:03,000\nHello\n"
    assertEquals("srt", subtitleExtensionFor(body))
  }

  @Test
  fun `a file whose url ends in a number is still identified by its content`() {
    // The real shape: https://subs5.strem.io/en/download/.../file/1962235234 carries no extension.
    assertEquals("vtt", subtitleExtensionFor("WEBVTT\n\n1\n00:00:01.000 --> 00:00:02.000\nx"))
  }

  @Test
  fun `utf-8 text decodes to itself`() {
    val text = "Café — naïve — Ça va ?"
    assertEquals(text, decodeSubtitleBytes(text.toByteArray(Charsets.UTF_8)))
  }

  @Test
  fun `a utf-8 byte order mark is stripped`() {
    val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "WEBVTT".toByteArray(Charsets.UTF_8)
    assertEquals("WEBVTT", decodeSubtitleBytes(bytes))
  }

  @Test
  fun `latin-1 text keeps its accents instead of becoming replacement characters`() {
    // OpenSubtitles serves CP1252 for plenty of titles; read as UTF-8 every accent is lost.
    val decoded = decodeSubtitleBytes("Café".toByteArray(Charsets.ISO_8859_1))
    assertEquals("Café", decoded)
    assertTrue("no replacement characters", !decoded.contains('\uFFFD'))
  }

  @Test
  fun `selectable text must contain timed cues`() {
    assertTrue(subtitleTextHasCues("WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nBonjour"))
    assertTrue(subtitleTextHasCues("1\n00:00:01,000 --> 00:00:03,000\nBonjour"))
    assertTrue(subtitleTextHasCues("[Events]\nDialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Bonjour"))
  }

  @Test
  fun `html error pages are not accepted as subtitle tracks`() {
    assertTrue(!subtitleTextHasCues("<html><body>Access denied</body></html>"))
  }

  @Test
  fun `utf-16 subtitles keep their text`() {
    val body = "1\n00:00:01,000 --> 00:00:03,000\nFrançais"
    val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + body.toByteArray(Charsets.UTF_16LE)
    assertEquals(body, decodeSubtitleBytes(bytes))
  }
}
