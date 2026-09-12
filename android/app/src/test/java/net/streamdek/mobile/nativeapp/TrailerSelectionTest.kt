package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The half of trailer handling that is decided rather than fetched: reading an id out of a URL,
 * and ranking a title's videos on what the metadata service said about them.
 */
class TrailerSelectionTest {

  // ---- URL parsing -------------------------------------------------------------------------

  @Test
  fun `reads the id out of every URL form a metadata service hands over`() {
    val expected = "dQw4w9WgXcQ"
    listOf(
      "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      "https://youtube.com/watch?v=dQw4w9WgXcQ&t=42s",
      "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
      "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
      "http://www.youtube.com/watch?app=desktop&v=dQw4w9WgXcQ",
      "https://youtu.be/dQw4w9WgXcQ",
      "https://youtu.be/dQw4w9WgXcQ?t=30",
      "https://www.youtube.com/embed/dQw4w9WgXcQ",
      "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?rel=0",
      "https://www.youtube.com/shorts/dQw4w9WgXcQ",
      "https://www.youtube.com/live/dQw4w9WgXcQ",
      "https://www.youtube.com/v/dQw4w9WgXcQ",
      "https://www.youtube.com/e/dQw4w9WgXcQ",
      "www.youtube.com/watch?v=dQw4w9WgXcQ",
      "dQw4w9WgXcQ",
    ).forEach { url ->
      assertEquals(url, expected, extractYoutubeTrailerKey(url))
    }
  }

  @Test
  fun `follows the one hop an attribution link puts in the way`() {
    // These arrive from services that route their outbound links through YouTube's own redirector.
    assertEquals(
      "dQw4w9WgXcQ",
      extractYoutubeTrailerKey("https://www.youtube.com/attribution_link?a=abc&u=%2Fwatch%3Fv%3DdQw4w9WgXcQ%26feature%3Dshare"),
    )
  }

  @Test
  fun `a missing or malformed trailer URL resolves to nothing rather than throwing`() {
    listOf(
      null,
      "",
      "   ",
      "not a url",
      "https://",
      "https://vimeo.com/123456789",
      "https://example.com/watch?v=dQw4w9WgXcQ", // Right shape, wrong host.
      "https://www.youtube.com/watch?v=tooshort",
      "https://www.youtube.com/watch?v=waaaaaaaaaaytoolong",
      "https://www.youtube.com/results?search_query=trailer",
      "https://www.youtube.com/@somechannel",
    ).forEach { url ->
      assertNull(url, extractYoutubeTrailerKey(url))
    }
  }

  @Test
  fun `does not mistake a channel or playlist page for a video`() {
    // The previous parser took the last path segment of any youtube.com URL, so a channel handle
    // eleven characters long became a video id and the resolver went looking for it.
    assertNull(extractYoutubeTrailerKey("https://www.youtube.com/c/AAAAAAAAAAA"))
    assertNull(extractYoutubeTrailerKey("https://www.youtube.com/playlist?list=PL1234567890A"))
  }

  @Test
  fun `recognises a trailer that is already a plain file`() {
    assertTrue(isNativePlayableTrailerUrl("https://cdn.example.com/trailers/abc.mp4"))
    assertTrue(isNativePlayableTrailerUrl("https://cdn.example.com/t.m3u8?token=abc&expires=1"))
    assertTrue(isNativePlayableTrailerUrl("https://cdn.example.com/t.mpd"))
    assertFalse(isNativePlayableTrailerUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    // A query that merely mentions mp4 is not an mp4.
    assertFalse(isNativePlayableTrailerUrl("https://example.com/play?format=.mp4"))
  }

  @Test
  fun `percent-decodes query values so a wrapped URL can be read`() {
    val parameters = trailerQueryParameters("https://x/y?a=one%20two&b=plus+sign&c")
    assertEquals("one two", parameters["a"])
    assertEquals("plus sign", parameters["b"])
    assertEquals("", parameters["c"])
  }

  // ---- Choosing which video is the trailer -------------------------------------------------

  private fun video(
    key: String,
    type: String? = null,
    name: String = "",
    official: Boolean = false,
    publishedAt: String? = null,
    season: Int? = null,
  ) = MediaTrailer(
    key = key,
    site = "YouTube",
    type = type,
    name = name,
    official = official,
    publishedAt = publishedAt,
    seasonNumber = season,
  )

  @Test
  fun `puts the official trailer ahead of the promotional run around it`() {
    // The order a metadata service actually returns: newest first, which around a release means
    // the ticket adverts sit on top of the trailer they are advertising.
    val ordered = orderTrailerCandidates(
      listOf(
        video("aaaaaaaaaaa", type = "Clip", name = "Now Playing", official = true, publishedAt = "2026-03-01"),
        video("bbbbbbbbbbb", type = "Featurette", name = "Behind the Scenes", official = true, publishedAt = "2026-02-20"),
        video("ccccccccccc", type = "Trailer", name = "Official Trailer", official = true, publishedAt = "2026-01-10"),
        video("ddddddddddd", type = "Teaser", name = "Teaser Trailer", official = true, publishedAt = "2025-11-01"),
      ),
    )
    assertEquals(listOf("ccccccccccc", "ddddddddddd", "aaaaaaaaaaa", "bbbbbbbbbbb"), ordered)
  }

  @Test
  fun `prefers the studio upload to a fan re-post of the same kind`() {
    val ordered = orderTrailerCandidates(
      listOf(
        video("fanfanfanfa", type = "Trailer", name = "Official Trailer", official = false),
        video("studiostudi", type = "Trailer", name = "Official Trailer", official = true),
      ),
    )
    assertEquals("studiostudi", ordered.first())
  }

  @Test
  fun `a series opens on its own trailer rather than on a season teaser`() {
    val ordered = orderTrailerCandidates(
      listOf(
        video("seasonfoure", type = "Trailer", name = "Season 4 Trailer", official = true, publishedAt = "2026-05-01", season = 4),
        video("seriestrail", type = "Trailer", name = "Official Trailer", official = true, publishedAt = "2024-01-01"),
      ),
    )
    assertEquals("seriestrail", ordered.first())
  }

  @Test
  fun `takes the newer of two trailers that are otherwise alike`() {
    val ordered = orderTrailerCandidates(
      listOf(
        video("olderoldero", type = "Trailer", name = "Official Trailer", official = true, publishedAt = "2025-01-01"),
        video("newernewern", type = "Trailer", name = "Official Trailer", official = true, publishedAt = "2026-01-01"),
      ),
    )
    assertEquals("newernewern", ordered.first())
  }

  @Test
  fun `ranks on the name when the service states no type at all`() {
    // What an add-on supplying a flat list of keys looks like once names are attached.
    val ordered = orderTrailerCandidates(
      listOf(
        video("interviewaa", name = "Cast Interview"),
        video("trailerbbbb", name = "Official Trailer"),
      ),
    )
    assertEquals("trailerbbbb", ordered.first())
  }

  @Test
  fun `keeps entries with no stated site and drops the ones from elsewhere`() {
    // A bare key list carries no site; that is not a reason to throw it away. A Vimeo entry is,
    // because nothing behind this can resolve one.
    val ordered = orderTrailerCandidates(
      listOf(
        MediaTrailer(key = "bareidbarei"),
        MediaTrailer(key = "123456789", site = "Vimeo"),
      ),
    )
    assertEquals(listOf("bareidbarei"), ordered)
  }

  @Test
  fun `counts the same video once however it was written`() {
    val ordered = orderTrailerCandidates(
      listOf(
        video("https://www.youtube.com/watch?v=dQw4w9WgXcQ", type = "Trailer"),
        video("https://youtu.be/dQw4w9WgXcQ", type = "Trailer"),
        video("dQw4w9WgXcQ", type = "Trailer"),
      ),
    )
    assertEquals(listOf("dQw4w9WgXcQ"), ordered)
  }

  @Test
  fun `an empty or unusable list orders to nothing`() {
    assertEquals(emptyList<String>(), orderTrailerCandidates(emptyList()))
    assertEquals(emptyList<String>(), orderTrailerCandidates(listOf(MediaTrailer(key = ""), MediaTrailer(key = "junk"))))
  }

  @Test
  fun `a stated trailer outranks a stated clip whatever the names say`() {
    assertTrue(
      trailerMetadataScore(video("a", type = "Trailer", name = "Untitled")) >
        trailerMetadataScore(video("b", type = "Clip", name = "Official Trailer Clip")),
    )
  }
}
