package net.streamdek.mobile.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of the episode sources page that can be decided without a screen.
 *
 * Two things are worth pinning down here. The first is the ambient palette: the page is painted a
 * colour taken from whatever still the episode happens to have, so the controls drawn on it are the
 * one place in the app where neither the foreground nor the background is known in advance, and
 * "looks fine on the screenshot I had" is not a guarantee. The second is the source filter's
 * arithmetic, because a chip that promises a number the filter then does not show is worse than no
 * number at all.
 */
class EpisodeStreamsPageTest {

  private val themeAccent = Color(0xFF7C5CFF)

  /** The page colour a dark-themed page would settle on for artwork of this hue. */
  private fun darkPage(red: Int, green: Int, blue: Int): Color =
    DominantColor.settleForBackground(red, green, blue, lightTheme = false)

  /** The same for a light-themed page. */
  private fun lightPage(red: Int, green: Int, blue: Int): Color =
    DominantColor.settleForBackground(red, green, blue, lightTheme = true)

  private fun hueOf(color: Color): Float =
    DominantColor.rgbToHsv(
      (color.red * 255).toInt(),
      (color.green * 255).toInt(),
      (color.blue * 255).toInt(),
    ).first

  private fun stream(addonName: String = "PenguPlay", name: String? = null) = AddonStream(
    addonId = "addon",
    addonName = addonName,
    name = name,
    title = null,
    description = null,
    url = "https://example.test/a.mkv",
    infoHash = null,
    fileIdx = null,
    filename = null,
    quality = null,
    size = null,
    cachedBy = emptyList(),
  )

  // --- ambient palette ---

  @Test
  fun `an accent stands clear of the page it sits on, whatever the artwork was`() {
    // Warm, cool, saturated and near-neutral artwork, dark theme and light theme alike. A selected
    // chip filled with a colour only a shade off the page is a chip nobody can see is selected.
    val pages = listOf(
      darkPage(214, 68, 40),    // warm orange-red
      darkPage(32, 96, 214),    // cool blue
      darkPage(18, 200, 96),    // saturated green
      darkPage(180, 40, 190),   // magenta
      lightPage(214, 68, 40),
      lightPage(32, 96, 214),
      lightPage(18, 200, 96),
      lightPage(180, 40, 190),
    )
    pages.forEach { page ->
      val accent = ambientAccentColor(page, themeAccent)
      assertTrue(
        "accent $accent is not distinguishable from page $page",
        contrastRatio(accent, page) >= 3.0f,
      )
    }
  }

  @Test
  fun `an accent keeps the artwork's hue rather than inventing one`() {
    // The point of the ambient palette is that the page and its controls belong together. An accent
    // that drifted to another hue would read as a second colour scheme laid over the first.
    listOf(darkPage(214, 68, 40), darkPage(32, 96, 214), lightPage(18, 200, 96)).forEach { page ->
      val drift = kotlin.math.abs(hueOf(ambientAccentColor(page, themeAccent)) - hueOf(page))
      assertTrue("hue drifted by $drift degrees", minOf(drift, 360f - drift) <= 2f)
    }
  }

  @Test
  fun `a page with no colour of its own falls back to the theme accent`() {
    // Grey artwork, a failed extraction and Normal background mode all land on a near-neutral page.
    // Amplifying the saturation of a grey gives a muddy near-grey, so the theme's accent is used.
    assertEquals(themeAccent, ambientAccentColor(Color(0xFF101215), themeAccent))
    assertEquals(themeAccent, ambientAccentColor(Color(0xFFF7F7F8), themeAccent))
    assertNotEquals(themeAccent, ambientAccentColor(darkPage(214, 68, 40), themeAccent))
  }

  @Test
  fun `label text on an accent is always readable`() {
    // Chip labels and primary buttons draw their text in this colour, so it has to clear AA on
    // every accent the page can produce - including the pale ones a dark page generates.
    listOf(
      darkPage(214, 68, 40),
      darkPage(32, 96, 214),
      lightPage(18, 200, 96),
      lightPage(180, 40, 190),
    ).forEach { page ->
      val accent = ambientAccentColor(page, themeAccent)
      assertTrue(
        "ink on accent $accent is only ${contrastRatio(readableOn(accent), accent)}:1",
        contrastRatio(readableOn(accent), accent) >= 4.5f,
      )
    }
  }

  @Test
  fun `contrast ratio is symmetric and scaled the way WCAG defines it`() {
    assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.05f)
    assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.05f)
    assertEquals(1f, contrastRatio(Color.White, Color.White), 0.001f)
  }

  @Test
  fun `a dark page keeps a light ink and a light page a dark one`() {
    assertTrue(readableOn(darkPage(32, 96, 214)).luminance() > 0.5f)
    assertTrue(readableOn(lightPage(32, 96, 214)).luminance() < 0.5f)
  }

  // --- source filter ---

  @Test
  fun `chip counts match what filtering by that chip will show`() {
    val streams = listOf(
      stream("PenguPlay"),
      stream("PenguPlay"),
      stream("Flix-Streams"),
      stream("PenguPlay"),
      stream("AIOStreams"),
    )
    val counts = streamProviderCounts(streams)
    assertEquals(mapOf("PenguPlay" to 3, "Flix-Streams" to 1, "AIOStreams" to 1), counts)
    counts.forEach { (provider, count) ->
      assertEquals(count, streams.count { it.addonName == provider })
    }
  }

  @Test
  fun `chip order follows the ranked list, so the best source leads`() {
    // Grouping and the filter row both read first-appearance order off the already-ranked list.
    // If the chips re-sorted themselves the row would reshuffle every time a source answered.
    val counts = streamProviderCounts(
      listOf(stream("Flix-Streams"), stream("PenguPlay"), stream("Flix-Streams")),
    )
    assertEquals(listOf("Flix-Streams", "PenguPlay"), counts.keys.toList())
  }

  @Test
  fun `a result with no add-on name is counted under the name it does carry`() {
    // Same fallback the grouping and the old filter row used, so a chip cannot name a group that
    // the sections below it do not have.
    val counts = streamProviderCounts(listOf(stream(addonName = "", name = "Server 3")))
    assertEquals(mapOf("Server 3" to 1), counts)
  }

  @Test
  fun `an unnamed result is left out of the chips rather than given a blank one`() {
    assertTrue(streamProviderCounts(listOf(stream(addonName = "", name = null))).isEmpty())
  }

  // --- search status wording ---

  // These check which shape the status line takes, not the words it comes out as: the wording
  // moved into string resources, so it is whichever language the viewer chose and a unit test
  // cannot resolve it. The branch chosen is the part that is logic.
  @Test
  fun `outstanding sources are named up to two and counted after that`() {
    assertEquals(SourceListPhrase.One("PenguPlay"), sourceListPhrase(listOf("PenguPlay")))
    assertEquals(
      SourceListPhrase.Two("PenguPlay", "AIOStreams"),
      sourceListPhrase(listOf("PenguPlay", "AIOStreams")),
    )
    assertEquals(
      SourceListPhrase.Counted("PenguPlay", 2),
      sourceListPhrase(listOf("PenguPlay", "AIOStreams", "Flix-Streams")),
    )
    // The status line is one line: a search across a dozen add-ons must not try to list them all.
    assertEquals(
      SourceListPhrase.Counted("A", 11),
      sourceListPhrase(('A'..'L').map { it.toString() }),
    )
  }

  @Test
  fun `a status with no names still reads as a sentence`() {
    assertEquals(SourceListPhrase.Unnamed, sourceListPhrase(emptyList()))
  }
}
