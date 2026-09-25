package net.streamdek.mobile.nativeapp

/** Contract: canonical tv is a series; Stremio-native tv is live. Unknown is never a movie. */
internal object MediaClassification {
  // Compiled once. This runs for every record a playback-progress pull looks at, and building the
  // pattern per call made the regex compiler the single hottest thing in that loop.
  private val separators = Regex("[\\s_-]+")
  private fun key(raw: String?): String = raw?.trim()?.lowercase()?.replace(separators, "").orEmpty()
  fun canonical(raw: String?): String = when (key(raw)) {
    "movie", "movies", "film", "films", "featurefilm", "featurefilms", "tvmovie" -> "movie"
    "tv", "series", "show", "shows", "tvshow", "tvshows", "tvseries", "television", "televisionshow", "televisionseries" -> "tv"
    "live", "livetv", "channel", "channels", "tvchannel", "tvchannels", "event", "events", "sport", "sports", "iptv" -> "live"
    else -> "unknown"
  }

  // Stremio catalogue resource `tv` means channels. Item `tv` elsewhere may mean series.
  fun addon(raw: String?): String = if (key(raw) == "tv") "live" else canonical(raw)

  fun item(raw: String?, catalog: String?, id: String, episodic: Boolean = false): String {
    val declared = canonical(raw)
    if (declared == "live" || (declared != "unknown" && key(raw) != "tv")) return declared
    val typedId = Regex("^tmdb:(movie|tv):[0-9]{1,12}$", RegexOption.IGNORE_CASE)
      .matchEntire(id.trim())?.groupValues?.get(1)?.lowercase()
    if (typedId != null) return typedId
    if (episodic) return "tv"
    if (key(raw) == "tv" && key(catalog) != "tv") return "tv"
    return addon(catalog)
  }

  fun enrichmentId(id: String, providerOwned: Boolean = false): String? {
    val value = id.trim()
    if (MetadataLookupIdentity.imdbId(value) != null) return MetadataLookupIdentity.imdbId(value)
    if (value.startsWith("tmdb:", true) && MetadataLookupIdentity.tmdbId(value) != null) return value
    return value.takeIf { !providerOwned && MetadataLookupIdentity.tmdbId(it) != null }
  }

  fun titleKey(value: String): String = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
  fun uniqueTitleMatch(title: String, year: String?, candidates: List<MediaItem>, type: String): MediaItem? {
    val key = titleKey(title)
    if (key.isBlank() || year?.take(4)?.toIntOrNull() == null) return null
    return candidates.filter {
      canonical(it.type) == type && titleKey(it.title) == key && it.year?.take(4) == year.take(4)
    }.distinctBy { it.id }.singleOrNull()
  }
}
