package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TheIntroDbClientTest {
  @Test fun `parses every valid credits window and preserves media-end marker`() {
    val media = TheIntroDbClient.parseMedia(
      """{"tmdb_id":12345,"type":"movie","credits":[{"start_ms":1800000,"end_ms":null},{"start_ms":1900000,"end_ms":1950000}]}""",
    )!!

    assertEquals(12345, media.tmdbId)
    assertEquals("movie", media.type)
    assertEquals(2, media.credits.size)
    assertEquals(1_800_000L, media.credits.first().startMs)
    assertNull(media.credits.first().endMs)
  }

  @Test fun `drops malformed credits without rejecting a usable response`() {
    val media = TheIntroDbClient.parseMedia(
      """{"tmdb_id":7,"type":"movie","credits":[{"start_ms":-1,"end_ms":4},{"start_ms":9000,"end_ms":8000},{"start_ms":10000,"end_ms":12000}]}""",
    )!!

    assertEquals(listOf(TheIntroDbTimestamp(10_000L, 12_000L)), media.credits)
  }

  @Test fun `normalizes a null intro start to the beginning of media`() {
    val media = TheIntroDbClient.parseMedia(
      """{"tmdb_id":7,"type":"movie","intro":[{"start_ms":null,"end_ms":23000}]}""",
    )!!

    assertEquals(listOf(TheIntroDbTimestamp(0L, 23_000L)), media.intro)
  }
}
