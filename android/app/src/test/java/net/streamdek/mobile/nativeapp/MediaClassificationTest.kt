package net.streamdek.mobile.nativeapp
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class MediaClassificationTest {
  @Test fun `common aliases normalize but original Stremio tv context stays distinguishable`() {
    for (alias in listOf("series", "tv", "tvshow", "tv_series", "show", " TV Show ", "television-series"))
      assertEquals("tv", MediaClassification.canonical(alias))
    for (alias in listOf("movie", "movies", "film", "films", "Feature Film", "tv_movie"))
      assertEquals("movie", MediaClassification.canonical(alias))
    assertEquals("tv", MediaClassification.item("tv", "other", "opaque"))
    assertEquals("tv", MediaClassification.item("tv", "tv", "opaque", episodic = true))
    assertEquals("tv", MediaClassification.item("tv", "tv", "tmdb:tv:1399"))
    assertEquals("live", MediaClassification.item("tv", "tv", "channel"))
    assertEquals("unknown", MediaClassification.item(null, null, "opaque"))
  }

  @Test fun `mixed catalog preserves item classification`() {
    assertEquals("movie", MediaClassification.item("film", "other", "provider:1"))
    assertEquals("tv", MediaClassification.item("series", "other", "provider:2"))
    assertEquals("live", MediaClassification.item("tv", "tv", "channel:1"))
    assertEquals("unknown", MediaClassification.item(null, "other", "provider:3"))
    assertEquals("tv", MediaClassification.item(null, "other", "tmdb:tv:1399"))
    assertEquals("tv", MediaClassification.item(null, "other", "provider:4", true))
    assertEquals("unknown", MediaClassification.item("anime", "other", "provider:5"))
  }
  @Test fun `opaque and provider numeric ids never become tmdb enrichment ids`() {
    for (id in listOf("cnc:1399", "cnc:tt1234567", "1399")) assertNull(MediaClassification.enrichmentId(id, true))
    assertEquals("1399", MediaClassification.enrichmentId("1399"))
    assertEquals("tmdb:tv:1399", MediaClassification.enrichmentId("tmdb:tv:1399", true))
  }
  @Test fun `unknown meta stays unknown`() {
    assertEquals("unknown", parseLocalAddonMetaResponse(JSONObject("""{"meta":{"id":"opaque","name":"Title"}}"""), "other", "opaque").type)
  }
  @Test fun `title enrichment requires unique same year same type exact title`() {
    val item = MediaItem(id="1", type="movie", title="Example", year="2024", poster=null, backdrop=null, rating=null, description="")
    assertEquals(item, MediaClassification.uniqueTitleMatch("Example", "2024", listOf(item), "movie"))
    assertNull(MediaClassification.uniqueTitleMatch("Example", null, listOf(item), "movie"))
    assertNull(MediaClassification.uniqueTitleMatch("Example", "2023", listOf(item), "movie"))
    assertNull(MediaClassification.uniqueTitleMatch("Example", "2024", listOf(item, item.copy(id="2")), "movie"))
  }
  @Test fun `vod URLs and provider names cannot turn movies into channels`() {
    assertFalse(MediaItem(id="movie:1",type="movie",title="Film",year=null,poster=null,backdrop=null,rating=null,description="",sourceAddonName="Live Movies",directStreamUrl="https://example.test/file.mp4").isLiveCatalogItem())
    assertTrue(MediaItem(id="channel:1",type="live",title="Channel",year=null,poster=null,backdrop=null,rating=null,description="").isLiveCatalogItem())
  }
}
