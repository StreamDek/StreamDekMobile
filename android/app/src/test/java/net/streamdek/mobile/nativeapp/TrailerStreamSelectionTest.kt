package net.streamdek.mobile.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the resolver does with a player response: which audio track, which rendition, which shape of
 * source, and what happens when the response is unusable.
 */
class TrailerStreamSelectionTest {

  private val avc = "video/mp4; codecs=\"avc1.640028\""
  private val vp9 = "video/webm; codecs=\"vp9\""
  private val m4a = "audio/mp4; codecs=\"mp4a.40.2\""
  private val opus = "audio/webm; codecs=\"opus\""

  private fun audioFormat(url: String, mime: String, bitrate: Int, language: String? = null, isDefault: Boolean = false) =
    JSONObject().put("url", url).put("mimeType", mime).put("bitrate", bitrate).apply {
      if (language != null) {
        put("audioTrack", JSONObject().put("displayName", language).put("audioIsDefault", isDefault))
      }
    }

  private fun array(vararg items: JSONObject): JSONArray = JSONArray().apply { items.forEach { put(it) } }

  // ---- Multi-language audio ----------------------------------------------------------------

  @Test
  fun `plays the original language when the upload carries dubs`() {
    // A real trailer, measured live: eight language tracks, and the German dub was encoded 42 bps
    // above the English original. Choosing on bitrate alone therefore played it in German.
    val formats = array(
      audioFormat("de", m4a, 130557, language = "German (DE)", isDefault = false),
      audioFormat("fr", m4a, 130520, language = "French (FR)", isDefault = false),
      audioFormat("en", m4a, 130515, language = "English (US) original", isDefault = true),
      audioFormat("it", m4a, 130482, language = "Italian", isDefault = false),
    )
    val chosen = selectAdaptiveAudio(formats)
    assertEquals("en", chosen?.url)
    assertTrue(chosen!!.isDefaultTrack)
  }

  @Test
  fun `takes the original language even when only a webm copy of it exists`() {
    // Language outranks container: a dub in m4a is still the wrong trailer.
    val formats = array(
      audioFormat("de-m4a", m4a, 130000, language = "German (DE)", isDefault = false),
      audioFormat("en-webm", opus, 120000, language = "English original", isDefault = true),
    )
    assertEquals("en-webm", selectAdaptiveAudio(formats)?.url)
  }

  @Test
  fun `a video with one audio stream needs no language flag`() {
    // Most uploads carry no audioTrack object at all. That is the only audio there is, so it is
    // the default — treating a missing flag as "not default" would reject every such video.
    val formats = array(
      audioFormat("low", m4a, 49886),
      audioFormat("high", m4a, 130470),
    )
    val chosen = selectAdaptiveAudio(formats)
    assertEquals("high", chosen?.url)
    assertTrue(chosen!!.isDefaultTrack)
  }

  @Test
  fun `still prefers m4a and the higher bitrate within the original language`() {
    val formats = array(
      audioFormat("en-opus-high", opus, 142816, language = "English", isDefault = true),
      audioFormat("en-m4a-low", m4a, 49895, language = "English", isDefault = true),
      audioFormat("en-m4a-high", m4a, 130625, language = "English", isDefault = true),
    )
    assertEquals("en-m4a-high", selectAdaptiveAudio(formats)?.url)
  }

  @Test
  fun `a response with no usable audio yields no audio rather than a wrong one`() {
    assertNull(selectAdaptiveAudio(null))
    assertNull(selectAdaptiveAudio(JSONArray()))
    // AC-3 in a container the merging source cannot take.
    assertNull(selectAdaptiveAudio(array(audioFormat("ac3", "audio/x-ac3", 384000))))
    // An entry with no URL is not a track.
    assertNull(selectAdaptiveAudio(array(JSONObject().put("mimeType", m4a).put("bitrate", 128000))))
  }

  // ---- The byte budget, and the client it applies to ----------------------------------------

  private fun videoFormat(url: String, mime: String, height: Int, bytes: Long? = null) =
    JSONObject().put("url", url).put("mimeType", mime).put("height", height).apply {
      if (bytes != null) put("contentLength", bytes.toString())
    }

  /** The renditions a real 148-second trailer offers, with their real sizes. */
  private val realTrailerRenditions = array(
    videoFormat("v1080", avc, 1080, 51_433_839),
    videoFormat("v720", avc, 720, 13_939_190),
    videoFormat("v480", avc, 480, 7_415_669),
    videoFormat("v360", avc, 360, 4_639_386),
  )

  @Test
  fun `an uncapped client plays the trailer at full resolution`() {
    // The whole point of leading the ladder with a client whose URLs serve any span: with no
    // budget in the way, the tallest rendition inside the ceiling wins.
    assertEquals("v1080" to 1080, selectAdaptiveVideo(realTrailerRenditions, 1080, Long.MAX_VALUE))
  }

  @Test
  fun `a capped client is held to what it can actually finish`() {
    // Same response, same trailer. This is what every StreamDek trailer used to look like, and why
    // they were all 360p: a capped client was at the head of the ladder, so nothing else was asked.
    assertEquals("v360" to 360, selectAdaptiveVideo(realTrailerRenditions, 1080, 7L * 1024 * 1024))
  }

  @Test
  fun `prefers an unthrottled URL over one carrying an n parameter at the same height`() {
    // googlevideo rate-limits a URL with an `n` until the value is deciphered by the player's own
    // JavaScript, which this app does not run. A stalled trailer reads as a broken one.
    val formats = array(
      videoFormat("https://r1.googlevideo.com/videoplayback?id=1&n=abcdef", avc, 1080),
      videoFormat("https://r2.googlevideo.com/videoplayback?id=2", vp9, 1080),
    )
    assertEquals("https://r2.googlevideo.com/videoplayback?id=2" to 1080, selectAdaptiveVideo(formats, 2160, Long.MAX_VALUE))
  }

  @Test
  fun `a throttled URL is still better than no trailer`() {
    val formats = array(videoFormat("https://r1.googlevideo.com/videoplayback?id=1&n=abcdef", avc, 1080))
    assertEquals(1080, selectAdaptiveVideo(formats, 2160, Long.MAX_VALUE)?.second)
  }

  @Test
  fun `reads the n parameter only when it is actually present`() {
    assertTrue(isThrottledGoogleVideoUrl("https://x.googlevideo.com/videoplayback?id=1&n=abc&clen=5"))
    assertFalse(isThrottledGoogleVideoUrl("https://x.googlevideo.com/videoplayback?id=1&clen=5"))
    // `mn` and `sn` merely end in n.
    assertFalse(isThrottledGoogleVideoUrl("https://x.googlevideo.com/videoplayback?mn=sn-a,sn-b&sn=1"))
    assertFalse(isThrottledGoogleVideoUrl("https://x.googlevideo.com/videoplayback"))
  }

  // ---- Fallback ordering between the shapes of source ---------------------------------------

  @Test
  fun `at equal quality prefers HLS then progressive then a merged pair`() {
    assertTrue(trailerSourceKindRank(TrailerSourceKind.HLS) < trailerSourceKindRank(TrailerSourceKind.PROGRESSIVE))
    assertTrue(trailerSourceKindRank(TrailerSourceKind.PROGRESSIVE) < trailerSourceKindRank(TrailerSourceKind.ADAPTIVE))
  }

  @Test
  fun `never trades picture quality for a simpler source`() {
    // The ordering above is a tiebreak, not a preference. Preferring progressive on sight is what
    // answered a client offering 1080p adaptive with its lone 360p muxed stream.
    val candidates = listOf(
      TrailerPlaybackSource("hls360", height = 360, kind = TrailerSourceKind.HLS),
      TrailerPlaybackSource("progressive360", height = 360, kind = TrailerSourceKind.PROGRESSIVE),
      TrailerPlaybackSource("adaptive1080", audioUrl = "a", height = 1080, kind = TrailerSourceKind.ADAPTIVE),
    )
    val best = candidates.maxWithOrNull(compareBy({ it.height ?: 0 }, { -trailerSourceKindRank(it.kind) }))
    assertEquals("adaptive1080", best?.url)
  }

  @Test
  fun `takes the HLS stream when it matches the best rendition on offer`() {
    val candidates = listOf(
      TrailerPlaybackSource("hls1080", height = 1080, kind = TrailerSourceKind.HLS),
      TrailerPlaybackSource("adaptive1080", audioUrl = "a", height = 1080, kind = TrailerSourceKind.ADAPTIVE),
    )
    val best = candidates.maxWithOrNull(compareBy({ it.height ?: 0 }, { -trailerSourceKindRank(it.kind) }))
    assertEquals("hls1080", best?.url)
  }

  // ---- HLS manifests -------------------------------------------------------------------------

  private val manifestUrl = "https://manifest.googlevideo.com/api/manifest/hls_variant/expire/123/file/index.m3u8"

  private val manifest = """
    #EXTM3U
    #EXT-X-STREAM-INF:BANDWIDTH=500000,CODECS="avc1.42001E,mp4a.40.2",RESOLUTION=640x360
    https://r1---sn-x.googlevideo.com/videoplayback/hls/360/index.m3u8
    #EXT-X-STREAM-INF:BANDWIDTH=2000000,CODECS="avc1.4d401f,mp4a.40.2",RESOLUTION=1280x720
    https://r1---sn-x.googlevideo.com/videoplayback/hls/720/index.m3u8
    #EXT-X-STREAM-INF:BANDWIDTH=4500000,CODECS="avc1.640028,mp4a.40.2",RESOLUTION=1920x1080
    https://r1---sn-x.googlevideo.com/videoplayback/hls/1080/index.m3u8
  """.trimIndent()

  /**
   * Note what is being asserted and what is not: [pickHlsVariant] finds the best variant so its
   * *height* can be compared against the other candidates. The URL handed to the player is always
   * the master — YouTube keeps the audio in separate `#EXT-X-MEDIA` renditions, so a variant
   * playlist on its own plays in silence.
   */
  @Test
  fun `reads the tallest variant inside the ceiling so HLS can be compared on height`() {
    assertEquals(
      "https://r1---sn-x.googlevideo.com/videoplayback/hls/1080/index.m3u8" to 1080,
      pickHlsVariant(manifest, manifestUrl, 2160),
    )
    assertEquals(
      "https://r1---sn-x.googlevideo.com/videoplayback/hls/720/index.m3u8" to 720,
      pickHlsVariant(manifest, manifestUrl, 720),
    )
  }

  @Test
  fun `shows the smallest variant rather than nothing when all of them exceed the ceiling`() {
    assertEquals(
      "https://r1---sn-x.googlevideo.com/videoplayback/hls/360/index.m3u8" to 360,
      pickHlsVariant(manifest, manifestUrl, 240),
    )
  }

  @Test
  fun `resolves a relative variant against the manifest it came from`() {
    val relative = """
      #EXTM3U
      #EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=640x360
      variant/360.m3u8
      #EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=1280x720
      /abs/720.m3u8
    """.trimIndent()
    assertEquals(
      "https://manifest.googlevideo.com/abs/720.m3u8" to 720,
      pickHlsVariant(relative, manifestUrl, 2160),
    )
    assertEquals(
      "https://manifest.googlevideo.com/api/manifest/hls_variant/expire/123/file/variant/360.m3u8" to 360,
      pickHlsVariant(relative, manifestUrl, 360),
    )
  }

  @Test
  fun `leaves a dubbed video to the adaptive path rather than to HLS`() {
    // Measured on a live manifest: every one of a trailer's eight audio renditions said
    // DEFAULT=NO, so Media3 would have chosen a language by device locale. The adaptive path has a
    // stated answer for this and HLS does not, so a dubbed video does not go down it.
    assertFalse(isHlsTrailerCandidate(manifestUrl, hasMultipleAudioTracks = true))
    assertTrue(isHlsTrailerCandidate(manifestUrl, hasMultipleAudioTracks = false))
  }

  @Test
  fun `no manifest is not an HLS candidate`() {
    assertFalse(isHlsTrailerCandidate(null, hasMultipleAudioTracks = false))
    assertFalse(isHlsTrailerCandidate("", hasMultipleAudioTracks = false))
  }

  @Test
  fun `spots an upload that carries dubs`() {
    val dubbed = array(
      audioFormat("de", m4a, 130557, language = "German (DE)"),
      audioFormat("en", m4a, 130515, language = "English (US) original", isDefault = true),
    )
    assertTrue(hasMultipleAudioTracks(dubbed))
    // One stated track is still one language, and no audioTrack at all is the common case.
    assertFalse(hasMultipleAudioTracks(array(audioFormat("en", m4a, 130515, language = "English", isDefault = true))))
    assertFalse(hasMultipleAudioTracks(array(audioFormat("a", m4a, 130470), audioFormat("b", opus, 126232))))
    assertFalse(hasMultipleAudioTracks(null))
  }

  @Test
  fun `a manifest that could not be read drops HLS out of the running`() {
    assertNull(pickHlsVariant("", manifestUrl, 1080))
    assertNull(pickHlsVariant("<html>404 Not Found</html>", manifestUrl, 1080))
    // A stream declared with no URL after it is not a variant.
    assertNull(pickHlsVariant("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1,RESOLUTION=640x360\n#EXT-X-ENDLIST", manifestUrl, 1080))
  }

  @Test
  fun `keeps a quoted attribute whole when it contains a comma`() {
    val attributes = parseHlsAttributes(
      """#EXT-X-STREAM-INF:BANDWIDTH=4500000,CODECS="avc1.640028,mp4a.40.2",RESOLUTION=1920x1080""",
    )
    assertEquals("4500000", attributes["BANDWIDTH"])
    assertEquals("avc1.640028,mp4a.40.2", attributes["CODECS"])
    assertEquals("1920x1080", attributes["RESOLUTION"])
  }

  // ---- Failure behaviour ---------------------------------------------------------------------

  @Test
  fun `an unplayable response produces no source instead of a broken one`() {
    // What a private, deleted or region-blocked video looks like by the time it reaches selection:
    // a response with nothing in it. Every selector has to answer null rather than improvise.
    assertNull(selectAdaptiveVideo(null, 1080))
    assertNull(selectAdaptiveVideo(JSONArray(), 1080))
    assertNull(selectProgressiveTrailer(null, 1080))
    assertNull(selectProgressiveTrailer(JSONArray(), 1080))
    assertNull(selectAdaptiveAudio(null))
  }

  @Test
  fun `a progressive format with no sound is not treated as playable on its own`() {
    val silent = array(videoFormat("video-only", avc, 720))
    assertNull(selectProgressiveTrailer(silent, 1080))
  }
}
