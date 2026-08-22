package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamGroupingTest {
  private fun stream(
    addonName: String = "Torrentio",
    name: String? = null,
    title: String? = null,
    filename: String? = null,
    description: String? = null,
    quality: String? = null,
    size: String? = null,
  ) = AddonStream(
    addonId = "addon",
    addonName = addonName,
    name = name,
    title = title,
    description = description,
    url = "https://example.test/a.mkv",
    infoHash = null,
    fileIdx = null,
    filename = filename,
    quality = quality,
    size = size,
    cachedBy = emptyList(),
  )

  @Test
  fun `quality comes from the source's own field before its text`() {
    // A 720p file whose name mentions a 2160p sample must not be filed under 4K.
    assertEquals(
      StreamQualityTier.Hd,
      streamQualityTier(stream(quality = "720p", title = "Movie.2160p.sample.mkv")),
    )
  }

  @Test
  fun `quality words are matched whole, so UHD and HDR do not read as HD`() {
    assertEquals(StreamQualityTier.Uhd, streamQualityTier(stream(title = "Movie 2026 UHD BluRay")))
    assertEquals(StreamQualityTier.Uhd, streamQualityTier(stream(title = "Movie 2026 2160p HDR10")))
    assertEquals(StreamQualityTier.Fhd, streamQualityTier(stream(title = "Movie 2026 1080p HDR x265")))
    assertEquals(StreamQualityTier.Hd, streamQualityTier(stream(title = "Movie 2026 720p WEB-DL")))
  }

  @Test
  fun `a camera recording is filed as one whatever resolution it claims`() {
    assertEquals(StreamQualityTier.Cam, streamQualityTier(stream(title = "Movie 2026 1080p HDCAM")))
  }

  @Test
  fun `a source that says nothing lands in its own band rather than being dropped`() {
    assertEquals(StreamQualityTier.Unknown, streamQualityTier(stream(name = "Server 3")))
  }

  @Test
  fun `bands run highest first on Auto and lead with the chosen quality otherwise`() {
    val tiers = listOf(StreamQualityTier.Uhd, StreamQualityTier.Fhd, StreamQualityTier.Hd)
    assertEquals(tiers, tiers.sortedBy { streamQualityBandOrder(it, "Auto") })
    assertEquals(
      listOf(StreamQualityTier.Fhd, StreamQualityTier.Uhd, StreamQualityTier.Hd),
      tiers.sortedBy { streamQualityBandOrder(it, "1080p") },
    )
    assertEquals(
      listOf(StreamQualityTier.Hd, StreamQualityTier.Uhd, StreamQualityTier.Fhd),
      tiers.sortedBy { streamQualityBandOrder(it, "720p") },
    )
  }

  @Test
  fun `a band is ordered largest first and puts unknown sizes last`() {
    val small = stream(title = "Movie 1080p", size = "1.2 GB")
    val large = stream(title = "Movie 1080p", size = "8.4 GB")
    val middle = stream(title = "Movie 1080p", size = "4 GB")
    val sizeless = stream(title = "Movie 1080p")
    val band = buildStreamSourceSections(listOf(small, sizeless, large, middle)).single().bands.single()
    assertEquals(listOf("8.4 GB", "4 GB", "1.2 GB", null), band.streams.map { it.size })
  }

  @Test
  fun `results split by source and then by quality`() {
    val sections = buildStreamSourceSections(
      listOf(
        stream(addonName = "Torrentio", title = "Movie 2160p", size = "20 GB"),
        stream(addonName = "Torrentio", title = "Movie 1080p", size = "5 GB"),
        stream(addonName = "Comet", title = "Movie 1080p", size = "6 GB"),
      ),
    )
    assertEquals(listOf("Torrentio", "Comet"), sections.map { it.source })
    assertEquals(listOf(2, 1), sections.map { it.total })
    assertEquals(
      listOf(StreamQualityTier.Uhd, StreamQualityTier.Fhd),
      sections.first().bands.map { it.tier },
    )
  }

  @Test
  fun `a result described only by resolution and size falls back to the title being watched`() {
    assertEquals("Dune Part Two", streamDisplayName(stream(name = "1080p"), "Dune Part Two"))
    assertEquals("Dune Part Two", streamDisplayName(stream(name = "1080p • 2.3 GB"), "Dune Part Two"))
    assertEquals("Dune Part Two", streamDisplayName(stream(name = "4K", title = "HEVC 10bit"), "Dune Part Two"))
  }

  @Test
  fun `a result that names itself keeps its own name`() {
    assertEquals(
      "Dune.Part.Two.2024.2160p.WEB-DL",
      streamDisplayName(stream(name = "Dune.Part.Two.2024.2160p.WEB-DL"), "Fallback"),
    )
    // A short label is still a label. Sources that lead with their own server name keep it.
    assertEquals("AniDB • English", streamDisplayName(stream(name = "AniDB • English"), "Fallback"))
  }

  @Test
  fun `a later field is used when the first one only describes the file`() {
    assertEquals(
      "Dune Part Two 2024",
      streamDisplayName(stream(name = "1080p", title = "Dune Part Two 2024"), "Fallback"),
    )
  }

  @Test
  fun `descriptor-only text is recognised as naming nothing`() {
    assertFalse(streamTextNamesTitle("1080p"))
    assertFalse(streamTextNamesTitle("2160p | 12.4 GB | HEVC"))
    assertFalse(streamTextNamesTitle(""))
    assertFalse(streamTextNamesTitle(null))
    assertTrue(streamTextNamesTitle("Sinners 2025 1080p"))
  }
}
