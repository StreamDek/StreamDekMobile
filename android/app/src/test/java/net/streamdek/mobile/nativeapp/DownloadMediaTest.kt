package net.streamdek.mobile.nativeapp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadMediaTest {
  private fun encoded(vararg pairs: Pair<String, Any?>): String =
    JSONObject().apply { pairs.forEach { (key, value) -> put(key, value) } }.toString()

  @Test
  fun readsBackTheMediaRecord() {
    val media = parseDownloadMedia(
      encoded(
        "v" to 1,
        "mediaId" to "1396",
        "mediaType" to "tv",
        "title" to "Breaking Bad",
        "year" to "2008",
        "poster" to "https://img/poster.jpg",
        "backdrop" to "https://img/backdrop.jpg",
        "seasonNumber" to 2,
        "episodeNumber" to 5,
        "episodeTitle" to "Breakage",
        "runtimeMinutes" to 47,
      ),
      fallbackTitle = "file.mp4",
    )

    assertEquals("1396", media.mediaId)
    assertEquals("tv", media.mediaType)
    assertEquals("Breaking Bad", media.title)
    assertEquals("https://img/poster.jpg", media.poster)
    assertEquals(2, media.seasonNumber)
    assertEquals(5, media.episodeNumber)
    assertEquals(47, media.runtimeMinutes)
    assertTrue(media.isResolvable)
  }

  @Test
  fun treatsALegacyTitleOnlyRecordAsUnresolvable() {
    // Downloads saved before the media record existed hold a bare title in `data`. They must keep
    // their title and be reported as unresolvable, never mistaken for a catalogue id.
    val media = parseDownloadMedia("The Matrix", fallbackTitle = "file.mp4")

    assertEquals("The Matrix", media.title)
    assertEquals("", media.mediaId)
    assertEquals("movie", media.mediaType)
    assertFalse(media.isResolvable)
  }

  @Test
  fun fallsBackToTheFilenameWhenNothingWasStored() {
    listOf(null, "").forEach { raw ->
      val media = parseDownloadMedia(raw, fallbackTitle = "file.mp4")
      assertEquals("file.mp4", media.title)
      assertFalse(media.isResolvable)
    }
  }

  @Test
  fun leavesOptionalFieldsNullForAMovie() {
    val media = parseDownloadMedia(
      encoded("v" to 1, "mediaId" to "603", "mediaType" to "movie", "title" to "The Matrix"),
      fallbackTitle = "file.mp4",
    )

    assertNull(media.seasonNumber)
    assertNull(media.episodeNumber)
    assertNull(media.poster)
    assertTrue(media.isResolvable)
  }

  @Test
  fun treatsAnExplicitlyNullFieldAsAbsent() {
    // JSONObject.put(key, null) drops the key, but a record round-tripped elsewhere can still
    // carry JSON nulls — those must not become the string "null".
    val media = parseDownloadMedia(
      """{"v":1,"mediaId":"603","mediaType":"movie","title":"The Matrix","poster":null,"seasonNumber":null}""",
      fallbackTitle = "file.mp4",
    )

    assertNull(media.poster)
    assertNull(media.seasonNumber)
    assertEquals("The Matrix", media.title)
  }
}
