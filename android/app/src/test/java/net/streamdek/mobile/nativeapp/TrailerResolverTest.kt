package net.streamdek.mobile.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerResolverTest {
  @Test
  fun trailerResolutionSupports4kAndClampsOutOfRangeValues() {
    assertEquals(360, normalizeTrailerMaxHeight(144))
    assertEquals(2160, normalizeTrailerMaxHeight(2160))
    assertEquals(2160, normalizeTrailerMaxHeight(4320))
  }

  private fun formats(vararg entries: Triple<String, String, Int>): JSONArray {
    val array = JSONArray()
    entries.forEach { (url, mime, height) ->
      array.put(JSONObject().put("url", url).put("mimeType", mime).put("height", height))
    }
    return array
  }

  /** Renditions with a declared size, which is what decides whether one can be finished. */
  private fun sizedFormats(vararg entries: List<Any>): JSONArray {
    val array = JSONArray()
    entries.forEach { (url, mime, height, bytes) ->
      array.put(
        JSONObject().put("url", url).put("mimeType", mime).put("height", height)
          .put("contentLength", bytes.toString()),
      )
    }
    return array
  }

  private val avc = "video/mp4; codecs=\"avc1.640028\""
  private val budget = 7L * 1024 * 1024

  @Test
  fun `prefers a rendition that can be played to the end over a taller one that cannot`() {
    // googlevideo serves only the first few megabytes of these URLs, so a 1080p trailer runs out
    // of obtainable bytes partway through. A smaller rendition that fits plays completely.
    val available = sizedFormats(
      listOf("v1080", avc, 1080, 20L * 1024 * 1024),
      listOf("v480", avc, 480, 5L * 1024 * 1024),
    )
    assertEquals("v480" to 480, selectAdaptiveVideo(available, 2160, budget))
  }

  @Test
  fun `still takes the tallest among the renditions that fit`() {
    val available = sizedFormats(
      listOf("v240", avc, 240, 2L * 1024 * 1024),
      listOf("v480", avc, 480, 5L * 1024 * 1024),
      listOf("v1080", avc, 1080, 20L * 1024 * 1024),
    )
    assertEquals("v480" to 480, selectAdaptiveVideo(available, 2160, budget))
  }

  @Test
  fun `falls back to an oversized rendition rather than showing nothing`() {
    val available = sizedFormats(listOf("v1080", avc, 1080, 20L * 1024 * 1024))
    assertEquals("v1080" to 1080, selectAdaptiveVideo(available, 2160, budget))
  }

  @Test
  fun `an adaptive pair beats the lone 360p muxed stream`() {
    // The headset client publishes exactly one progressive format and it is 360p. Preferring
    // progressive on sight threw away a 1080p adaptive rendition and ignored the quality setting.
    val progressive = JSONArray().put(
      JSONObject().put("url", "muxed360").put("mimeType", avc).put("height", 360)
        .put("audioQuality", "AUDIO_QUALITY_LOW"),
    )
    val adaptive = sizedFormats(listOf("v1080", avc, 1080, 4L * 1024 * 1024))
    val progressiveHeight = selectProgressiveTrailer(progressive, 2160)?.height
    val adaptiveHeight = selectAdaptiveVideo(adaptive, 2160, budget)?.second
    assertEquals(360, progressiveHeight)
    assertEquals(1080, adaptiveHeight)
    // The resolver takes the taller of the two, so the adaptive pair wins.
    assertTrue(adaptiveHeight!! > progressiveHeight!!)
  }

  @Test
  fun `keeps honouring the resolution ceiling`() {
    val available = sizedFormats(
      listOf("v1080", avc, 1080, 1L * 1024 * 1024),
      listOf("v480", avc, 480, 1L * 1024 * 1024),
    )
    assertEquals("v480" to 480, selectAdaptiveVideo(available, 480, budget))
  }

  private fun audio(vararg entries: Pair<String, Pair<String, Int>>): JSONArray {
    val array = JSONArray()
    entries.forEach { (url, spec) ->
      array.put(JSONObject().put("url", url).put("mimeType", spec.first).put("bitrate", spec.second))
    }
    return array
  }

  @Test
  fun `reaches 2160p through vp9 when avc stops at 1080p`() {
    // YouTube publishes nothing above 1080p in AVC, so accepting only avc1 was what capped
    // trailers at 1080p no matter how the resolution setting was configured.
    val available = formats(
      Triple("avc1080", "video/mp4; codecs=\"avc1.640028\"", 1080),
      Triple("vp9-2160", "video/webm; codecs=\"vp9\"", 2160),
      Triple("av1-1440", "video/mp4; codecs=\"av01.0.12M.08\"", 1440),
    )
    assertEquals("vp9-2160" to 2160, selectAdaptiveVideo(available, 2160))
  }

  @Test
  fun `prefers avc at the same height for decoder compatibility`() {
    val available = formats(
      Triple("vp9-1080", "video/webm; codecs=\"vp9\"", 1080),
      Triple("avc-1080", "video/mp4; codecs=\"avc1.640028\"", 1080),
    )
    assertEquals("avc-1080" to 1080, selectAdaptiveVideo(available, 2160))
  }

  @Test
  fun `never exceeds the configured resolution`() {
    val available = formats(
      Triple("vp9-2160", "video/webm; codecs=\"vp9\"", 2160),
      Triple("avc-720", "video/mp4; codecs=\"avc1.640028\"", 720),
    )
    assertEquals("avc-720" to 720, selectAdaptiveVideo(available, 720))
  }

  @Test
  fun `ranks codecs avc over vp9 over av1`() {
    assertEquals(3, trailerCodecRank("video/mp4; codecs=\"avc1.640028\""))
    assertEquals(2, trailerCodecRank("video/webm; codecs=\"vp9\""))
    assertEquals(1, trailerCodecRank("video/mp4; codecs=\"av01.0.12M.08\""))
    assertEquals(0, trailerCodecRank("video/x-unknown"))
  }

  @Test
  fun `ignores video formats in codecs the player cannot take`() {
    assertNull(selectAdaptiveVideo(formats(Triple("weird", "video/x-unknown", 2160)), 2160))
  }

  @Test
  fun `falls back to webm audio so a vp9 pick still has sound`() {
    assertEquals("opus", selectAdaptiveAudio(audio("opus" to ("audio/webm; codecs=\"opus\"" to 160000))))
  }

  @Test
  fun `picks the trailer over the promotional run that precedes it`() {
    // The real order a metadata service returns for a released film: the marketing cutdowns are
    // newest, so they come first, and taking the head of the list opened the page on a
    // fifteen-second theatre sting instead of the trailer.
    val candidates = listOf(
      TrailerCandidate("promo1", "Vision ASMR 15 OWN NOW 16x9", 15),
      TrailerCandidate("promo2", "Dune: Part Two | \"#1 Movie in the World\" | Now Playing", 15),
      TrailerCandidate("promo3", "Dune: Part Two | \"A Remarkable Achievement\" | Now Playing", 60),
      TrailerCandidate("promo4", "Dune: Part Two | Tickets on Sale Now", 30),
      TrailerCandidate("real", "Dune: Part Two | Official Trailer 2", 183),
    )
    assertEquals("real", pickBestTrailerCandidate(candidates))
  }

  @Test
  fun `a trailer that never says trailer still beats a longer promo`() {
    val candidates = listOf(
      TrailerCandidate("promo", "Barbie Streaming Exclusively on Max", 70),
      TrailerCandidate("main", "Barbie | Main Trailer", 161),
    )
    assertEquals("main", pickBestTrailerCandidate(candidates))
  }

  @Test
  fun `prefers the fuller cut when two are both real trailers`() {
    val candidates = listOf(
      TrailerCandidate("teaser", "Barbie | Teaser Trailer", 75),
      TrailerCandidate("full", "Barbie | Main Trailer", 161),
    )
    assertEquals("full", pickBestTrailerCandidate(candidates))
  }

  @Test
  fun `keeps the only video a title has even when it looks like a promo`() {
    // Better a short promo than a hero with nothing in it.
    assertEquals("only", pickBestTrailerCandidate(listOf(TrailerCandidate("only", "Now Playing", 15))))
    assertNull(pickBestTrailerCandidate(emptyList()))
  }

  @Test
  fun `running time outweighs marketing language on both sides`() {
    // "In Theaters Now" appears on genuine trailers, so the wording alone must not sink one...
    assertTrue(trailerCandidateScore("Official Trailer | In Theaters December 25", 145) > 0)
    // ...and a promo cutdown must not be rescued by having "trailer" in its name.
    assertTrue(trailerCandidateScore("Trailer | Tickets on Sale Now", 15) < 0)
  }

  @Test
  fun `sets aside videos that are a different format entirely`() {
    assertTrue(
      trailerCandidateScore("Official Trailer", 150) > trailerCandidateScore("Behind the Scenes", 150),
    )
    assertTrue(trailerCandidateScore("Cast Interview", 150) < trailerCandidateScore("Trailer", 150))
  }

  @Test
  fun `prefers m4a audio over webm even at a lower bitrate`() {
    val available = audio(
      "opus" to ("audio/webm; codecs=\"opus\"" to 160000),
      "m4a" to ("audio/mp4; codecs=\"mp4a.40.2\"" to 128000),
    )
    assertEquals("m4a", selectAdaptiveAudio(available))
  }
}
