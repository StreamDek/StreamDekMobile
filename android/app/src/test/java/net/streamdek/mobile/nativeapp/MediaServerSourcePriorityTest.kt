package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

/** "Prefer media server source": Direct Play, then Direct Stream, then add-ons, then transcodes. */
class MediaServerSourcePriorityTest {

  private fun stream(
    title: String,
    addonId: String = "addon.a",
    source: String? = null,
    quality: String? = null,
    size: String? = null,
  ) = AddonStream(
    addonId = addonId,
    addonName = addonId,
    name = null,
    title = title,
    description = null,
    url = "https://x/$title",
    infoHash = null,
    fileIdx = null,
    filename = null,
    quality = quality,
    size = size,
    cachedBy = emptyList(),
    source = source,
  )

  private val plexPlay4k = stream("Plex 4K DP", "mediaserver:plex:s1", "plex:directplay", "4K", "60 GB")
  private val jellyfinPlay1080 = stream("Jellyfin 1080p DP", "mediaserver:jellyfin:s2", "jellyfin:directplay", "1080p", "12 GB")
  private val embyStream4k = stream("Emby 4K DS", "mediaserver:emby:s3", "emby:directstream", "4K")
  private val plexTranscode = stream("Plex 1080p transcode", "mediaserver:plex:s1", "plex:transcode", "1080p")
  private val addon4kDv = stream("Movie 2160p DV REMUX", "addon.a")
  private val plugin1080 = stream("Movie 1080p WEB-DL x264", "plugin:p")
  private val all = listOf(plexTranscode, addon4kDv, plugin1080, embyStream4k, jellyfinPlay1080, plexPlay4k)

  @Test
  fun `with the preference on, server play methods lead in tiers and transcodes trail`() {
    val ranked = rankedStreams(all, hasDebrid = false, preferMediaServer = true)
    assertEquals(listOf(plexPlay4k, jellyfinPlay1080, embyStream4k), ranked.take(3))
    assertEquals(setOf(addon4kDv, plugin1080), ranked.subList(3, 5).toSet())
    assertEquals(plexTranscode, ranked.last())
  }

  @Test
  fun `a compatible Direct Play outranks a higher resolution Direct Stream`() {
    val ranked = rankedStreams(listOf(embyStream4k, jellyfinPlay1080), hasDebrid = false, preferMediaServer = true)
    assertEquals(jellyfinPlay1080, ranked.first())
  }

  @Test
  fun `a server Direct Stream outranks a better add-on`() {
    val ranked = rankedStreams(listOf(addon4kDv, embyStream4k), hasDebrid = false, preferMediaServer = true)
    assertEquals(embyStream4k, ranked.first())
  }

  @Test
  fun `favourite add-ons stay first among add-ons but not above the servers`() {
    val ranked = rankedStreams(
      listOf(plugin1080, addon4kDv, jellyfinPlay1080),
      hasDebrid = false,
      favouriteAddonIds = setOf("addon.a"),
      preferMediaServer = true,
    )
    assertEquals(listOf(jellyfinPlay1080, addon4kDv, plugin1080), ranked)
  }

  @Test
  fun `within a tier the better copy leads, and the preferred quality is respected`() {
    val play720 = stream("Jellyfin 720p DP", "mediaserver:jellyfin:s2", "jellyfin:directplay", "720p")
    val playDv = stream("Plex DP 4K Dolby Vision", "mediaserver:plex:s1", "plex:directplay", "4K")
    assertEquals(listOf(playDv, plexPlay4k, jellyfinPlay1080, play720), rankedStreams(listOf(play720, jellyfinPlay1080, plexPlay4k, playDv), hasDebrid = false, preferMediaServer = true))
    assertEquals(jellyfinPlay1080, rankedStreams(listOf(plexPlay4k, jellyfinPlay1080), hasDebrid = false, preferredQuality = "1080p", preferMediaServer = true).first())
  }

  @Test
  fun `with the preference off the order is exactly the old one`() {
    assertEquals(rankedStreams(all, hasDebrid = false), rankedStreams(all, hasDebrid = false, preferMediaServer = false))
  }

  @Test
  fun `sources are classified by the play method the planner chose`() {
    assertEquals(SourceTier.ServerDirectPlay, sourceTierOf(plexPlay4k))
    assertEquals(SourceTier.ServerDirectStream, sourceTierOf(embyStream4k))
    assertEquals(SourceTier.ServerTranscode, sourceTierOf(plexTranscode))
    assertEquals(SourceTier.AddonOrPlugin, sourceTierOf(addon4kDv))
    assertEquals(SourceTier.AddonOrPlugin, sourceTierOf(stream("x", "addon.a", "plex:directplay")))
  }
}
