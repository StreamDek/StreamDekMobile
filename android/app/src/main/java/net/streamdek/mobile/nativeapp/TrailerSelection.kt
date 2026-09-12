package net.streamdek.mobile.nativeapp

/**
 * Everything about trailers that is decided rather than fetched.
 *
 * Reading a YouTube id out of a URL, ranking a title's videos, judging a rendition — none of it
 * touches the network or the Android framework, so all of it lives here as plain functions a unit
 * test can call directly. TrailerResolver does the talking; this file does the deciding.
 */

private val youtubeVideoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

/** Path prefixes that put the video id in the next segment rather than in `?v=`. */
private val youtubeIdBearingSegments = setOf("embed", "shorts", "live", "v", "e")

/**
 * The YouTube id [url] refers to, or null if it does not name one.
 *
 * Written against the raw string rather than `android.net.Uri` on purpose. The URL forms that reach
 * this are supplied by metadata services and add-ons, which means all of them turn up sooner or
 * later — watch links, `youtu.be`, embeds, Shorts, premiere and live pages, the old `/v/` and `/e/`
 * players, `youtube-nocookie.com`, `music.youtube.com`, and bare eleven-character ids. Parsing them
 * without the framework is what lets every one of those forms be covered by a test rather than by
 * hope: `Uri` is a stub in a JVM unit test and returns null for everything.
 */
internal fun extractYoutubeTrailerKey(url: String?): String? {
  val raw = url?.trim().orEmpty()
  if (raw.isEmpty()) return null
  if (youtubeVideoIdPattern.matches(raw)) return raw
  return parseYoutubeTrailerKey(raw, allowIndirection = true)
}

private fun parseYoutubeTrailerKey(raw: String, allowIndirection: Boolean): String? {
  val withoutFragment = raw.substringBefore('#')
  val withoutScheme = withoutFragment.substringAfter("://", withoutFragment)
  val authority = withoutScheme.substringBefore('/').substringBefore('?')
  val host = authority.substringAfterLast('@')
    .substringBefore(':')
    .lowercase()
    .removePrefix("www.")
    .removePrefix("m.")
  if (host.isEmpty()) return null

  val path = withoutScheme.removePrefix(authority).substringBefore('?')
  val segments = path.split('/').filter { it.isNotBlank() }
  val query = trailerQueryParameters(withoutFragment)

  val candidate = when {
    host == "youtu.be" || host.endsWith(".youtu.be") -> segments.firstOrNull()
    isYoutubeHost(host) -> when (segments.firstOrNull()?.lowercase()) {
      in youtubeIdBearingSegments -> segments.getOrNull(1)
      else -> query["v"]
    }
    else -> null
  }
  candidate?.takeIf { youtubeVideoIdPattern.matches(it) }?.let { return it }

  // `attribution_link` and the consent interstitial both wrap the real watch URL in a parameter.
  // One hop is enough — these do not nest — and refusing to recurse further means a malformed or
  // self-referential URL cannot spin here.
  if (!allowIndirection || !isYoutubeHost(host)) return null
  return listOfNotNull(query["u"], query["q"], query["continue"], query["next"])
    .firstNotNullOfOrNull { nested ->
      val absolute = if (nested.startsWith("/")) "https://www.youtube.com$nested" else nested
      parseYoutubeTrailerKey(absolute, allowIndirection = false)
    }
}

private fun isYoutubeHost(host: String): Boolean =
  host == "youtube.com" || host.endsWith(".youtube.com") ||
    host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")

/** Query parameters of [url], percent-decoded. A repeated name keeps its first value. */
internal fun trailerQueryParameters(url: String): Map<String, String> {
  val query = url.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
  if (query.isEmpty()) return emptyMap()
  val parameters = LinkedHashMap<String, String>()
  query.split('&').forEach { pair ->
    if (pair.isBlank()) return@forEach
    val separator = pair.indexOf('=')
    val name = if (separator < 0) pair else pair.substring(0, separator)
    val value = if (separator < 0) "" else pair.substring(separator + 1)
    val decoded = percentDecode(name)
    if (decoded.isNotEmpty() && !parameters.containsKey(decoded)) parameters[decoded] = percentDecode(value)
  }
  return parameters
}

private fun percentDecode(value: String): String {
  if ('%' !in value && '+' !in value) return value
  val out = StringBuilder(value.length)
  var index = 0
  while (index < value.length) {
    val ch = value[index]
    when {
      ch == '+' -> {
        out.append(' ')
        index++
      }
      ch == '%' && index + 2 < value.length -> {
        val hex = value.substring(index + 1, index + 3).toIntOrNull(16)
        if (hex == null) {
          out.append(ch)
          index++
        } else {
          out.append(hex.toChar())
          index += 3
        }
      }
      else -> {
        out.append(ch)
        index++
      }
    }
  }
  return out.toString()
}

/**
 * Whether [url] names a file the player can open with no extraction at all.
 *
 * Add-ons sometimes supply a trailer as a plain MP4 or an HLS playlist. Those need no resolving,
 * and sending one round the YouTube path could only fail. The extension is read from the path
 * rather than the whole string so a signed URL with a query does not stop being recognised.
 */
internal fun isNativePlayableTrailerUrl(url: String): Boolean {
  val path = url.substringBefore('?').substringBefore('#').lowercase()
  return path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm") ||
    path.endsWith(".mov") || path.endsWith(".m3u8") || path.endsWith(".mpd")
}

/**
 * One of a title's videos, as the metadata service described it.
 *
 * These fields used to be discarded at the parse layer: only the bare YouTube keys survived, in
 * whatever order they arrived. Everything downstream then had to work out which video was the
 * trailer by fetching each one's running time — a network request per candidate, for an answer the
 * metadata already contained. Keeping the description is what makes [orderTrailerCandidates] work.
 */
data class MediaTrailer(
  val key: String,
  val site: String? = null,
  /** The service's own category: "Trailer", "Teaser", "Clip", "Featurette", "Behind the Scenes". */
  val type: String? = null,
  val name: String? = null,
  /** Whether the studio published it, as opposed to a fan or an aggregator channel. */
  val official: Boolean = false,
  /** ISO-8601. Consulted only to break ties between videos of the same kind. */
  val publishedAt: String? = null,
  /** Rendition height the service advertises. A weak signal, and the last one read. */
  val sizeHeight: Int? = null,
  /** Non-null for a video attached to one season rather than to the series as a whole. */
  val seasonNumber: Int? = null,
)

/**
 * How strongly a described video looks like the title's main trailer.
 *
 * Read from what the service stated rather than inferred from the text, which is the whole point of
 * carrying the description this far. A season-specific video ranks below anything attached to the
 * series itself: opening a show's page on the season four teaser is wrong even when that teaser is
 * the newest thing in the list.
 */
internal fun trailerMetadataScore(trailer: MediaTrailer): Int {
  val type = trailer.type.orEmpty()
  val name = trailer.name.orEmpty().lowercase()
  var score = when {
    type.equals("Trailer", ignoreCase = true) -> 60
    type.equals("Teaser", ignoreCase = true) -> 40
    type.isBlank() -> 20 // Undescribed: neither trusted nor punished.
    else -> 0 // Clip, Featurette, Behind the Scenes, Bloopers, Opening Credits.
  }
  if (trailer.official) score += 15
  if (trailer.seasonNumber != null) score -= 30
  // The name still gets a say, because add-on supplied lists routinely carry no type at all, and
  // there a video called "Official Trailer" should not rank level with one called "Cast Interview".
  score += when {
    name.contains("official trailer") -> 8
    name.contains("trailer") -> 5
    name.contains("teaser") -> 3
    else -> 0
  }
  val otherFormat = listOf("featurette", "behind the scenes", "blooper", "interview", "tv spot", "opening scene")
  if (otherFormat.any { name.contains(it) }) score -= 12
  return score
}

/**
 * A title's videos, best trailer first.
 *
 * The order metadata services return is roughly newest first, which around a release is a wall of
 * ticket adverts with the actual trailer somewhere underneath. Ranking on what each video says it
 * is puts the trailer at the head before a single network request is made — so resolution starts
 * with the right video rather than discovering it by fetching fourteen running times.
 *
 * Non-YouTube entries are dropped: the extraction path behind this only speaks YouTube. An entry
 * with no stated site is kept, because that is what an add-on supplying bare keys looks like.
 */
internal fun orderTrailers(trailers: List<MediaTrailer>): List<MediaTrailer> =
  trailers
    .asSequence()
    .filter { it.site == null || it.site.equals("YouTube", ignoreCase = true) }
    // The key is normalised to the bare id here so everything downstream compares like with like:
    // the same video arriving once as a watch URL and once as an id is one candidate, not two.
    .mapNotNull { trailer -> extractYoutubeTrailerKey(trailer.key)?.let { trailer.copy(key = it) } }
    .distinctBy { it.key }
    .sortedWith(
      compareByDescending<MediaTrailer> { trailerMetadataScore(it) }
        .thenByDescending { it.publishedAt.orEmpty() }
        .thenByDescending { it.sizeHeight ?: 0 },
    )
    .toList()

/** [orderTrailers], as the bare YouTube ids the resolver takes. */
internal fun orderTrailerCandidates(trailers: List<MediaTrailer>): List<String> =
  orderTrailers(trailers).map { it.key }
