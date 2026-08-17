package net.streamdek.mobile.nativeapp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Which video is a title's trailer, according to KinoCheck.
 *
 * The metadata service's own list is not a list of trailers. It is every video a studio published,
 * newest first, which in the weeks around a release is a wall of ticket adverts: "NOW PLAYING"
 * stings of six to fifteen seconds, IMAX spots, social cutdowns. The resolver ranks them on running
 * time to find the real trailer among them, and does well, but it cannot invent one — for a title
 * whose fourteen entries are all adverts, the best available answer is a twenty-five second advert.
 *
 * KinoCheck publishes one curated trailer per title, keyed by the same TMDB id already in hand, and
 * marks each video with what it is. So the pick is read rather than inferred, Shorts never enter the
 * list, and titles whose studio upload was taken down still have something to play.
 *
 * Only the *identity* comes from here. KinoCheck hosts no video: the answer is a YouTube id, and
 * playback is the same resolver path as everything else, so the fallback behind this is the whole
 * existing pipeline rather than nothing.
 */
private const val kinocheckTag = "KinoCheck"

/**
 * How far into a KinoCheck video the trailer itself starts.
 *
 * Their uploads open with a branded sting. Three and a half seconds clears it while leaving the
 * trailer's own first frame intact — five overshot into the opening shot, so playback begins past it — but only
 * when the URL in hand will serve a span that does not start at byte zero. See
 * TrailerPlaybackSource.seekable: the gated client answers 403 to a mid-file range, and asking it
 * for one does not skip the sting, it kills the trailer.
 */
const val KINOCHECK_START_MS = 3_500L

/**
 * How much of a KinoCheck upload's tail to leave unplayed.
 *
 * Their videos close with a channel outro rather than with the trailer, so the hero would otherwise
 * end on branding. Ten seconds clears it.
 */
const val KINOCHECK_END_TRIM_MS = 10_000L



/**
 * Held lazily and rebuilt on demand, so a scheduled clear that deletes the cache directory from
 * under it does not leave this client writing into a directory that no longer exists.
 */
private var kinocheckClientRef: OkHttpClient? = null

@Synchronized
private fun kinocheckHttpClient(context: android.content.Context?): OkHttpClient =
    kinocheckClientRef ?: OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        // Trailer lookups cache into the trailer pipeline's own directory rather than a shared one,
        // so clearing trailer state cannot take add-on responses or artwork with it.
        .apply {
            context?.let { ctx ->
                runCatching { cache(okhttp3.Cache(TrailerCache.directory(ctx), TrailerCache.MAX_BYTES)) }
            }
        }
        .build()
        .also { kinocheckClientRef = it }

/**
 * Dropped after a clear so the next lookup opens a fresh cache in the recreated directory, and the
 * in-memory answers go with it.
 *
 * The misses are the ones that matter: a title KinoCheck failed to answer for while the pipeline was
 * broken stays remembered as "no trailer" for the rest of the session, which is one of the reasons
 * clearing the cache used to need an app restart before trailers came back.
 */
@Synchronized
internal fun resetKinocheckHttpClient() {
    runCatching { kinocheckClientRef?.cache?.close() }
    kinocheckClientRef = null
    kinocheckCache.clear()
}

/**
 * Answers are a property of the title and do not go stale, so one lookup per title per run is
 * plenty. Misses are cached too — a title KinoCheck does not carry will not start carrying it
 * while the viewer is on the page, and asking again on every recomposition would be a request per
 * scroll.
 */
private val kinocheckCache = LinkedHashMap<String, String?>()
private const val KINOCHECK_CACHE_SIZE = 128

@Synchronized
private fun cachedKinocheck(key: String): Pair<Boolean, String?> =
    if (kinocheckCache.containsKey(key)) true to kinocheckCache[key] else false to null

@Synchronized
private fun cacheKinocheck(key: String, value: String?) {
    if (kinocheckCache.size >= KINOCHECK_CACHE_SIZE) {
        kinocheckCache.keys.firstOrNull()?.let(kinocheckCache::remove)
    }
    kinocheckCache[key] = value
}

/**
 * The YouTube id of this title's trailer, or null if KinoCheck does not carry it.
 *
 * [tmdbId] is the id the metadata service already uses; [type] is its "movie" or "tv". English is
 * requested explicitly because the API answers in German by default, and a German-language response
 * carries no trailer at all for most titles — it returns the KinoCheck Originals reel instead.
 */
suspend fun kinocheckTrailerKey(
    tmdbId: String,
    type: String,
    context: android.content.Context? = null,
): String? = withContext(Dispatchers.IO) {
    val numericId = Regex("\\d{2,}").find(tmdbId)?.value ?: return@withContext null
    val endpoint = if (type.equals("tv", ignoreCase = true) || type.equals("series", ignoreCase = true)) "shows" else "movies"
    val cacheKey = "$endpoint:$numericId"
    val (hit, cached) = cachedKinocheck(cacheKey)
    if (hit) return@withContext cached

    val key = withTimeoutOrNull(8_000) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.kinocheck.com/$endpoint?tmdb_id=$numericId&language=en")
                .header("Accept", "application/json")
                .build()
            kinocheckHttpClient(context).newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(kinocheckTag, "$cacheKey: HTTP ${response.code}")
                    return@use null
                }
                pickKinocheckTrailer(JSONObject(response.body?.string().orEmpty()))
            }
        }.onFailure { Log.w(kinocheckTag, "$cacheKey: ${it.message}") }.getOrNull()
    }
    Log.d(kinocheckTag, "$cacheKey -> ${key ?: "none"}")
    cacheKinocheck(cacheKey, key)
    key
}

/**
 * The trailer out of a KinoCheck response.
 *
 * The top-level `trailer` is their own pick and is taken when present. Otherwise the videos are
 * searched for one marked `Trailer` — the other categories are `Clip`, `News`, `Original` and
 * `Compilation`, none of which is what was asked for. Because the category is stated rather than
 * guessed at, a Short cannot arrive here by being short; the defensive title check is for the day
 * they start tagging one as a trailer anyway.
 */
internal fun pickKinocheckTrailer(json: JSONObject): String? {
    fun usable(video: JSONObject?): String? {
        val id = video?.optString("youtube_video_id")?.ifBlank { null } ?: return null
        val title = video.optString("title").orEmpty()
        if (title.contains("#short", ignoreCase = true)) return null
        return id
    }
    usable(json.optJSONObject("trailer"))?.let { return it }
    val videos = json.optJSONArray("videos") ?: return null
    for (index in 0 until videos.length()) {
        val video = videos.optJSONObject(index) ?: continue
        val categories = video.optJSONArray("categories") ?: continue
        val isTrailer = (0 until categories.length()).any { categories.optString(it).equals("Trailer", ignoreCase = true) }
        if (!isTrailer) continue
        usable(video)?.let { return it }
    }
    return null
}
