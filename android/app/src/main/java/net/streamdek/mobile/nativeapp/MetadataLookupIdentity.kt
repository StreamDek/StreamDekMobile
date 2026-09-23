package net.streamdek.mobile.nativeapp

/** Keep provider identities intact; only recognised namespaces may become catalogue IDs. */
internal object MetadataLookupIdentity {
  fun supportsType(type: String): Boolean = type in setOf("movie", "tv")
  fun requireType(raw: String): String = MediaClassification.canonical(raw).also { require(supportsType(it)) }

  // The backend unwraps known namespaces and otherwise asks the owning add-on.
  fun detailIds(id: String): List<String> = listOf(id.trim()).filter(String::isNotEmpty)

  private val imdbPattern = Regex("^(?:imdb:)?(tt[0-9]{6,12})(?::[0-9]+(?::[0-9]+)?)?$", RegexOption.IGNORE_CASE)

  fun imdbId(value: String): String? = imdbPattern.matchEntire(value.trim())?.groupValues?.get(1)?.lowercase()

  private val tmdbPattern = Regex("^(?:tmdb:(?:(?:movie|tv):)?)?([0-9]{1,12})$", RegexOption.IGNORE_CASE)

  fun tmdbId(value: String): String? = tmdbPattern.matchEntire(value.trim())?.groupValues?.get(1)
}
