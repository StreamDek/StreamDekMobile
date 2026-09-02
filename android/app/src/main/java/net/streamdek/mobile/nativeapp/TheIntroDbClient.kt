package net.streamdek.mobile.nativeapp

import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.JsonParser

internal data class TheIntroDbTimestamp(
  val startMs: Long,
  val endMs: Long?,
)

internal data class TheIntroDbMedia(
  val tmdbId: Int?,
  val type: String?,
  val intro: List<TheIntroDbTimestamp>,
  val recap: List<TheIntroDbTimestamp>,
  val credits: List<TheIntroDbTimestamp>,
  val preview: List<TheIntroDbTimestamp>,
)

/** Typed Kotlin transport for the public TheIntroDB v3 media contract. */
internal class TheIntroDbClient(private val http: OkHttpClient) {
  fun getMedia(tmdbId: Int, season: Int? = null, episode: Int? = null, durationMs: Long? = null, apiKey: String? = null): Result<TheIntroDbMedia> = runCatching {
    require(tmdbId > 0) { "A positive TMDB id is required." }
    val url = buildString {
      append("https://api.theintrodb.org/v3/media?tmdb_id=")
      append(tmdbId)
      season?.let { append("&season=").append(it) }
      episode?.let { append("&episode=").append(it) }
      durationMs?.takeIf { it > 0 }?.let { append("&duration_ms=").append(it) }
    }
    val request = Request.Builder().url(url).header("Accept", "application/json")
      .apply { apiKey?.trim()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
      .build()
    http.newCall(request).apply { timeout().timeout(4500, java.util.concurrent.TimeUnit.MILLISECONDS) }.execute().use { response ->
      if (!response.isSuccessful) error("TheIntroDB returned HTTP ${response.code}.")
      parseMedia(response.body?.string().orEmpty())
        ?: error("TheIntroDB returned an invalid media response.")
    }
  }

  companion object {
    internal fun parseMedia(body: String): TheIntroDbMedia? = runCatching {
      val root = JsonParser.parseString(body).asJsonObject
      fun segments(name: String) = root.getAsJsonArray(name)?.mapNotNull { raw ->
        val entry = raw.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val startNode = entry.get("start_ms") ?: return@mapNotNull null
        val start = if (startNode.isJsonNull) 0L else {
          runCatching { startNode.asLong }.getOrNull()?.takeIf { it >= 0L } ?: return@mapNotNull null
        }
        val end = entry.get("end_ms")?.takeUnless { it.isJsonNull }?.let { runCatching { it.asLong }.getOrNull() }
        if (end != null && end < start) return@mapNotNull null
        TheIntroDbTimestamp(start, end)
      }.orEmpty()
      TheIntroDbMedia(
        tmdbId = root.get("tmdb_id")?.takeUnless { it.isJsonNull }?.let { runCatching { it.asInt }.getOrNull() }?.takeIf { it > 0 },
        type = root.get("type")?.takeUnless { it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }?.takeIf { it == "movie" || it == "tv" },
        intro = segments("intro"),
        recap = segments("recap"),
        credits = segments("credits"),
        preview = segments("preview"),
      )
    }.getOrNull()
  }
}
