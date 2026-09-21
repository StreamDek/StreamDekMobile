package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

/** The stream ranking keys each stream once now; the order it produces must not have changed. */
class StreamRankingTest {

  private fun stream(title: String, addonId: String = "a", size: String? = null, url: String? = "https://x/$title") = AddonStream(
    addonId = addonId,
    addonName = addonId,
    name = null,
    title = title,
    description = null,
    url = url,
    infoHash = null,
    fileIdx = null,
    filename = null,
    quality = null,
    size = size,
    cachedBy = emptyList(),
  )

  @Test
  fun `favourites lead, then score, then name`() {
    val ranked = rankedStreams(
      streams = listOf(
        stream("Movie 720p", addonId = "plain"),
        stream("Movie 2160p", addonId = "plain"),
        stream("Movie 720p", addonId = "fav"),
        stream("B 1080p", addonId = "plain"),
        stream("A 1080p", addonId = "plain"),
      ),
      hasDebrid = false,
      favouriteAddonIds = setOf("fav"),
    )
    assertEquals(
      listOf("fav:Movie 720p", "plain:Movie 2160p", "plain:A 1080p", "plain:B 1080p", "plain:Movie 720p"),
      ranked.map { "${it.addonId}:${it.title}" },
    )
  }

  @Test
  fun `the size cap still reads sizes from the release text`() {
    val ranked = rankedStreams(
      streams = listOf(stream("Big 1080p 40 GB"), stream("Small 1080p", size = "2.1 GB"), stream("Bitrate 7.71 Mbps")),
      hasDebrid = false,
      maxFileSizeGb = 10,
    )
    assertEquals(listOf("Small 1080p", "Bitrate 7.71 Mbps"), ranked.map { it.title })
  }

  @Test
  fun `sizes parse in every unit`() {
    assertEquals(2.0, parseStreamSizeGiB("2 GB")!!, 1e-9)
    assertEquals(0.5, parseStreamSizeGiB("512 MiB")!!, 1e-9)
    assertEquals(1024.0, parseStreamSizeGiB("1 TB")!!, 1e-9)
    assertEquals(null, parseStreamSizeGiB("7.71 Mbps"))
  }
}
